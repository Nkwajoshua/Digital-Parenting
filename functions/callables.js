const crypto = require('node:crypto')
const admin = require('firebase-admin')
const { FieldValue, Timestamp } = require('firebase-admin/firestore')
const { onCall, HttpsError } = require('firebase-functions/v2/https')
const { logger } = require('firebase-functions')

if (admin.apps.length === 0) admin.initializeApp()

const db = admin.firestore()

const PAIRING_TTL_MS = 15 * 60 * 1000
const PAIRING_ATTEMPTS = 8
const PAIRING_ISSUE_MIN_INTERVAL_MS = 10 * 1000
const COMMAND_TYPES = new Set(['block_app', 'unblock_app', 'set_limit'])
const ALERT_SEVERITIES = new Set(['info', 'warning', 'critical'])

function requireAuth(request) {
  if (!request.auth?.uid) {
    throw new HttpsError('unauthenticated', 'Authentication is required.')
  }
  return request.auth.uid
}

function signInProvider(request) {
  return request.auth?.token?.firebase?.sign_in_provider || null
}

function requireParent(request) {
  const uid = requireAuth(request)
  if (signInProvider(request) === 'anonymous') {
    throw new HttpsError('permission-denied', 'A parent account is required.')
  }
  return uid
}

function requireChild(request) {
  const uid = requireAuth(request)
  if (signInProvider(request) !== 'anonymous') {
    throw new HttpsError('permission-denied', 'A child device identity is required.')
  }
  return uid
}

function cleanString(value, fieldName, { required = true, max = 200 } = {}) {
  if (value == null || value === '') {
    if (required) throw new HttpsError('invalid-argument', `${fieldName} is required.`)
    return ''
  }
  if (typeof value !== 'string') {
    throw new HttpsError('invalid-argument', `${fieldName} must be a string.`)
  }
  const cleaned = value.trim()
  if (required && !cleaned) {
    throw new HttpsError('invalid-argument', `${fieldName} is required.`)
  }
  if (cleaned.length > max) {
    throw new HttpsError('invalid-argument', `${fieldName} is too long.`)
  }
  return cleaned
}

function positiveInt(value, fieldName, max) {
  if (!Number.isInteger(value) || value <= 0 || value > max) {
    throw new HttpsError('invalid-argument', `${fieldName} must be an integer between 1 and ${max}.`)
  }
  return value
}

async function assertParentOwnsChild(parentUid, childUid, tx = null) {
  const ref = db.collection('children').doc(childUid)
  const snap = tx ? await tx.get(ref) : await ref.get()
  if (!snap.exists || snap.get('parentUid') !== parentUid || snap.get('paired') !== true) {
    throw new HttpsError('permission-denied', 'The parent does not own this child device.')
  }
  return snap
}

exports.createPairingCode = onCall(async (request) => {
  const parentUid = requireParent(request)
  const nowMs = Date.now()
  const expiresAt = Timestamp.fromMillis(nowMs + PAIRING_TTL_MS)
  const rateRef = db.collection('control_rate_limits').doc(`pairing_${parentUid}`)

  for (let attempt = 0; attempt < PAIRING_ATTEMPTS; attempt += 1) {
    const code = String(crypto.randomInt(100000, 1000000))
    const ref = db.collection('pairing_codes').doc(code)

    try {
      await db.runTransaction(async (tx) => {
        const rateSnap = await tx.get(rateRef)
        const existing = await tx.get(ref)

        const lastIssuedAt = rateSnap.exists ? rateSnap.get('lastIssuedAt') : null
        const lastIssuedAtMs = lastIssuedAt?.toMillis?.() || 0
        if (lastIssuedAtMs && nowMs - lastIssuedAtMs < PAIRING_ISSUE_MIN_INTERVAL_MS) {
          const retryAfterSeconds = Math.max(
            1,
            Math.ceil((PAIRING_ISSUE_MIN_INTERVAL_MS - (nowMs - lastIssuedAtMs)) / 1000),
          )
          throw new HttpsError(
            'resource-exhausted',
            `Please wait ${retryAfterSeconds} seconds before generating another pairing code.`,
          )
        }

        if (existing.exists) throw new Error('PAIRING_CODE_COLLISION')

        tx.set(ref, {
          code,
          parentUid,
          status: 'pending',
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
          expiresAt,
          usedByChildUid: null,
          usedAt: null,
        })

        tx.set(rateRef, {
          parentUid,
          action: 'createPairingCode',
          lastIssuedAt: Timestamp.fromMillis(nowMs),
          updatedAt: FieldValue.serverTimestamp(),
        }, { merge: true })
      })

      logger.info('[PAIRING] Server pairing code created.', { parentUid, attempt })
      return { code, expiresAtMillis: expiresAt.toMillis() }
    } catch (error) {
      if (error?.message === 'PAIRING_CODE_COLLISION') continue
      if (error instanceof HttpsError) throw error

      logger.error('[PAIRING] Failed to create pairing code.', { parentUid, error: error.message })
      throw new HttpsError('internal', 'Unable to create a pairing code.')
    }
  }

  throw new HttpsError('resource-exhausted', 'Unable to allocate a pairing code. Try again.')
})

exports.redeemPairingCode = onCall(async (request) => {
  const childUid = requireChild(request)
  const code = cleanString(request.data?.code, 'code', { max: 6 })
  if (!/^\d{6}$/.test(code)) {
    throw new HttpsError('invalid-argument', 'Pairing code must contain exactly six digits.')
  }

  const deviceName = cleanString(request.data?.deviceName, 'deviceName', { required: false, max: 120 })
  const platform = cleanString(request.data?.platform || 'android', 'platform', { max: 20 })
  const codeRef = db.collection('pairing_codes').doc(code)
  const childRef = db.collection('children').doc(childUid)

  const result = await db.runTransaction(async (tx) => {
    const codeSnap = await tx.get(codeRef)
    if (!codeSnap.exists) throw new HttpsError('not-found', 'Pairing code was not found.')

    const pairing = codeSnap.data() || {}
    if (pairing.status !== 'pending') {
      throw new HttpsError('failed-precondition', 'Pairing code has already been used or expired.')
    }
    if (!pairing.expiresAt?.toMillis || pairing.expiresAt.toMillis() <= Date.now()) {
      throw new HttpsError('deadline-exceeded', 'Pairing code has expired.')
    }
    if (!pairing.parentUid) {
      throw new HttpsError('failed-precondition', 'Pairing code is missing its parent owner.')
    }

    const childSnap = await tx.get(childRef)
    const existing = childSnap.exists ? childSnap.data() || {} : {}
    if (existing.paired === true && existing.parentUid && existing.parentUid !== pairing.parentUid) {
      throw new HttpsError('failed-precondition', 'This child device is already paired to another parent.')
    }

    tx.set(childRef, {
      parentUid: pairing.parentUid,
      paired: true,
      pairedAt: FieldValue.serverTimestamp(),
      pairingCode: code,
      deviceName: deviceName || existing.deviceName || 'Android device',
      platform,
      monitoringActive: false,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true })

    tx.update(codeRef, {
      status: 'used',
      usedByChildUid: childUid,
      usedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    })

    return { parentUid: pairing.parentUid }
  })

  logger.info('[PAIRING] Pairing code redeemed.', { childUid, parentUid: result.parentUid })
  return { paired: true, parentUid: result.parentUid }
})

exports.sendCommand = onCall(async (request) => {
  const parentUid = requireParent(request)
  const childUid = cleanString(request.data?.childUid, 'childUid', { max: 128 })
  const type = cleanString(request.data?.type, 'type', { max: 40 })
  const appPackage = cleanString(request.data?.appPackage, 'appPackage', { max: 220 })
  const appName = cleanString(request.data?.appName, 'appName', { required: false, max: 160 })

  if (!COMMAND_TYPES.has(type)) {
    throw new HttpsError('invalid-argument', 'Unsupported command type.')
  }

  const commandRef = db.collection('children').doc(childUid).collection('commands').doc()

  await db.runTransaction(async (tx) => {
    await assertParentOwnsChild(parentUid, childUid, tx)

    const command = {
      parentUid,
      childUid,
      type,
      appPackage,
      appName: appName || appPackage,
      status: 'pending',
      createdAt: FieldValue.serverTimestamp(),
    }

    if (type === 'block_app') {
      command.reason = cleanString(request.data?.reason || 'Blocked by parent', 'reason', { max: 240 })
    }

    if (type === 'set_limit') {
      if (request.data?.enabled != null && typeof request.data.enabled !== 'boolean') {
        throw new HttpsError('invalid-argument', 'enabled must be a boolean.')
      }
      command.maxMinutes = positiveInt(request.data?.maxMinutes, 'maxMinutes', 1440)
      command.enabled = request.data?.enabled !== false
    }

    tx.set(commandRef, command)
  })

  logger.info('[COMMAND] Server-authorized command queued.', {
    parentUid,
    childUid,
    commandId: commandRef.id,
    type,
    appPackage,
  })

  return { commandId: commandRef.id, status: 'pending' }
})

exports.resolveTimeRequest = onCall(async (request) => {
  const parentUid = requireParent(request)
  const requestId = cleanString(request.data?.requestId, 'requestId', { max: 128 })
  const action = cleanString(request.data?.action, 'action', { max: 20 })
  if (!['approve', 'deny'].includes(action)) {
    throw new HttpsError('invalid-argument', 'action must be approve or deny.')
  }

  const requestRef = db.collection('time_requests').doc(requestId)

  const result = await db.runTransaction(async (tx) => {
    const requestSnap = await tx.get(requestRef)
    if (!requestSnap.exists) throw new HttpsError('not-found', 'Time request was not found.')

    const data = requestSnap.data() || {}
    if (!data.childUid) throw new HttpsError('failed-precondition', 'Time request has no child owner.')
    if (data.status !== 'pending') throw new HttpsError('failed-precondition', 'Time request is no longer pending.')

    await assertParentOwnsChild(parentUid, data.childUid, tx)

    const approved = action === 'approve'
    const approvedMinutes = approved
      ? positiveInt(request.data?.approvedMinutes, 'approvedMinutes', 240)
      : 0

    tx.update(requestRef, {
      status: approved ? 'approved' : 'denied',
      parentResponse: approved ? 'approved' : 'denied',
      approvedMinutes,
      resolvedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    })

    return { childUid: data.childUid, status: approved ? 'approved' : 'denied', approvedMinutes }
  })

  logger.info('[TIME_REQUEST] Server-authorized request resolution.', {
    parentUid,
    requestId,
    childUid: result.childUid,
    status: result.status,
  })

  return result
})

exports.reportChildSecurityAlert = onCall(async (request) => {
  const childUid = requireChild(request)
  const type = cleanString(request.data?.type, 'type', { max: 80 })
  const severity = cleanString(request.data?.severity || 'warning', 'severity', { max: 20 })
  const title = cleanString(request.data?.title, 'title', { max: 120 })
  const body = cleanString(request.data?.body, 'body', { max: 500 })

  if (!ALERT_SEVERITIES.has(severity)) {
    throw new HttpsError('invalid-argument', 'Invalid alert severity.')
  }

  const childSnap = await db.collection('children').doc(childUid).get()
  if (!childSnap.exists || childSnap.get('paired') !== true || !childSnap.get('parentUid')) {
    throw new HttpsError('failed-precondition', 'Child device is not paired.')
  }

  const parentUid = childSnap.get('parentUid')
  await db.collection('parent_notifications').add({
    parentUid,
    childUid,
    type,
    severity,
    title,
    body,
    createdAt: FieldValue.serverTimestamp(),
    read: false,
  })

  return { accepted: true }
})
