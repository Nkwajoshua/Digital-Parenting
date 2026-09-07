import assert from 'node:assert/strict'
import test from 'node:test'
import { getApps, initializeApp } from 'firebase-admin/app'
import { getFirestore, Timestamp } from 'firebase-admin/firestore'

const PROJECT_ID = process.env.GCLOUD_PROJECT || 'demo-digital-parenting-callables'
const AUTH_HOST = process.env.FIREBASE_AUTH_EMULATOR_HOST || '127.0.0.1:9099'
const FUNCTIONS_HOST = process.env.FUNCTIONS_EMULATOR_HOST || '127.0.0.1:5001'
const REGION = 'us-central1'

process.env.GCLOUD_PROJECT = PROJECT_ID

if (getApps().length === 0) initializeApp({ projectId: PROJECT_ID })
const db = getFirestore()

async function signUp({ email, password } = {}) {
  const payload = { returnSecureToken: true }
  if (email) payload.email = email
  if (password) payload.password = password

  const response = await fetch(
    `http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(payload),
    },
  )

  const body = await response.json()
  assert.equal(response.ok, true, `Auth emulator signup failed: ${JSON.stringify(body)}`)
  assert.ok(body.localId)
  assert.ok(body.idToken)
  return { uid: body.localId, token: body.idToken, email: body.email || null }
}

async function callCallable(name, token, data = {}) {
  const headers = { 'content-type': 'application/json' }
  if (token) headers.authorization = `Bearer ${token}`

  const response = await fetch(
    `http://${FUNCTIONS_HOST}/${PROJECT_ID}/${REGION}/${name}`,
    {
      method: 'POST',
      headers,
      body: JSON.stringify({ data }),
    },
  )

  const text = await response.text()
  let body = null
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = { raw: text }
  }

  return { httpStatus: response.status, ok: response.ok, body }
}

function assertCallableSuccess(response) {
  assert.equal(
    response.ok,
    true,
    `Expected callable success, got HTTP ${response.httpStatus}: ${JSON.stringify(response.body)}`,
  )
  assert.ok(response.body && Object.prototype.hasOwnProperty.call(response.body, 'result'))
  return response.body.result
}

function assertCallableError(response, expectedStatus) {
  assert.equal(response.ok, false, `Expected callable failure, got: ${JSON.stringify(response.body)}`)
  assert.equal(
    response.body?.error?.status,
    expectedStatus,
    `Expected ${expectedStatus}, got HTTP ${response.httpStatus}: ${JSON.stringify(response.body)}`,
  )
  return response.body.error
}

async function getDoc(path) {
  const snap = await db.doc(path).get()
  return snap.exists ? { id: snap.id, ...snap.data() } : null
}

test('server-authoritative callable control plane', async (t) => {
  const parentA = await signUp({ email: 'parent-a@example.test', password: 'test-parent-a' })
  const parentB = await signUp({ email: 'parent-b@example.test', password: 'test-parent-b' })
  const childA = await signUp()
  const childB = await signUp()
  const unpairedChild = await signUp()

  let parentACode
  let parentBCode

  await t.test('createPairingCode requires Parent identity and enforces issuance throttle', async () => {
    assertCallableError(await callCallable('createPairingCode', null), 'UNAUTHENTICATED')
    assertCallableError(await callCallable('createPairingCode', childA.token), 'PERMISSION_DENIED')

    const created = assertCallableSuccess(await callCallable('createPairingCode', parentA.token))
    assert.match(created.code, /^\d{6}$/)
    assert.ok(created.expiresAtMillis > Date.now())
    parentACode = created.code

    const pairing = await getDoc(`pairing_codes/${parentACode}`)
    assert.equal(pairing.parentUid, parentA.uid)
    assert.equal(pairing.status, 'pending')

    assertCallableError(
      await callCallable('createPairingCode', parentA.token),
      'RESOURCE_EXHAUSTED',
    )
  })

  await t.test('redeemPairingCode accepts Child identity and atomically establishes ownership', async () => {
    assertCallableError(
      await callCallable('redeemPairingCode', parentA.token, {
        code: parentACode,
        deviceName: 'Wrong-role device',
        platform: 'android',
      }),
      'PERMISSION_DENIED',
    )

    const redeemed = assertCallableSuccess(
      await callCallable('redeemPairingCode', childA.token, {
        code: parentACode,
        deviceName: 'Child A Phone',
        platform: 'android',
      }),
    )

    assert.equal(redeemed.paired, true)
    assert.equal(redeemed.parentUid, parentA.uid)

    const child = await getDoc(`children/${childA.uid}`)
    assert.equal(child.parentUid, parentA.uid)
    assert.equal(child.paired, true)
    assert.equal(child.pairingCode, parentACode)
    assert.equal(child.deviceName, 'Child A Phone')

    const pairing = await getDoc(`pairing_codes/${parentACode}`)
    assert.equal(pairing.status, 'used')
    assert.equal(pairing.usedByChildUid, childA.uid)

    assertCallableError(
      await callCallable('redeemPairingCode', childB.token, {
        code: parentACode,
        deviceName: 'Child B Phone',
        platform: 'android',
      }),
      'FAILED_PRECONDITION',
    )
  })

  await t.test('a paired Child cannot silently move to another Parent', async () => {
    const created = assertCallableSuccess(await callCallable('createPairingCode', parentB.token))
    parentBCode = created.code

    assertCallableError(
      await callCallable('redeemPairingCode', childA.token, {
        code: parentBCode,
        deviceName: 'Child A Phone',
        platform: 'android',
      }),
      'FAILED_PRECONDITION',
    )

    const child = await getDoc(`children/${childA.uid}`)
    assert.equal(child.parentUid, parentA.uid)
  })

  await t.test('sendCommand verifies Parent ownership and validates command payloads', async () => {
    assertCallableError(
      await callCallable('sendCommand', childA.token, {
        childUid: childA.uid,
        type: 'block_app',
        appPackage: 'com.example.app',
        appName: 'Example App',
      }),
      'PERMISSION_DENIED',
    )

    assertCallableError(
      await callCallable('sendCommand', parentB.token, {
        childUid: childA.uid,
        type: 'block_app',
        appPackage: 'com.example.app',
        appName: 'Example App',
      }),
      'PERMISSION_DENIED',
    )

    const queued = assertCallableSuccess(
      await callCallable('sendCommand', parentA.token, {
        childUid: childA.uid,
        type: 'block_app',
        appPackage: 'com.example.app',
        appName: 'Example App',
        reason: 'Parent test block',
      }),
    )

    assert.equal(queued.status, 'pending')
    assert.ok(queued.commandId)

    const command = await getDoc(`children/${childA.uid}/commands/${queued.commandId}`)
    assert.equal(command.parentUid, parentA.uid)
    assert.equal(command.childUid, childA.uid)
    assert.equal(command.type, 'block_app')
    assert.equal(command.status, 'pending')
    assert.equal(command.reason, 'Parent test block')

    assertCallableError(
      await callCallable('sendCommand', parentA.token, {
        childUid: childA.uid,
        type: 'set_limit',
        appPackage: 'com.example.app',
        appName: 'Example App',
        maxMinutes: 1441,
      }),
      'INVALID_ARGUMENT',
    )
  })

  await t.test('resolveTimeRequest permits only the owning Parent and only once', async () => {
    const approveRef = db.collection('time_requests').doc('integration-approve')
    await approveRef.set({
      childUid: childA.uid,
      deviceName: 'Child A Phone',
      appName: 'Example App',
      appPackage: 'com.example.app',
      requestedMinutes: 30,
      approvedMinutes: null,
      status: 'pending',
      parentResponse: null,
      createdAt: Timestamp.now(),
      resolvedAt: null,
    })

    assertCallableError(
      await callCallable('resolveTimeRequest', parentB.token, {
        requestId: approveRef.id,
        action: 'approve',
        approvedMinutes: 30,
      }),
      'PERMISSION_DENIED',
    )

    const approved = assertCallableSuccess(
      await callCallable('resolveTimeRequest', parentA.token, {
        requestId: approveRef.id,
        action: 'approve',
        approvedMinutes: 30,
      }),
    )
    assert.equal(approved.childUid, childA.uid)
    assert.equal(approved.status, 'approved')
    assert.equal(approved.approvedMinutes, 30)

    const approvedDoc = await getDoc(`time_requests/${approveRef.id}`)
    assert.equal(approvedDoc.status, 'approved')
    assert.equal(approvedDoc.parentResponse, 'approved')
    assert.equal(approvedDoc.approvedMinutes, 30)

    assertCallableError(
      await callCallable('resolveTimeRequest', parentA.token, {
        requestId: approveRef.id,
        action: 'deny',
      }),
      'FAILED_PRECONDITION',
    )

    const denyRef = db.collection('time_requests').doc('integration-deny')
    await denyRef.set({
      childUid: childA.uid,
      deviceName: 'Child A Phone',
      appName: 'Example App',
      appPackage: 'com.example.app',
      requestedMinutes: 15,
      approvedMinutes: null,
      status: 'pending',
      parentResponse: null,
      createdAt: Timestamp.now(),
      resolvedAt: null,
    })

    const denied = assertCallableSuccess(
      await callCallable('resolveTimeRequest', parentA.token, {
        requestId: denyRef.id,
        action: 'deny',
      }),
    )
    assert.equal(denied.status, 'denied')
    assert.equal(denied.approvedMinutes, 0)
  })

  await t.test('reportChildSecurityAlert routes only paired Child alerts to the owning Parent', async () => {
    assertCallableError(
      await callCallable('reportChildSecurityAlert', parentA.token, {
        type: 'integration_alert_parent',
        severity: 'warning',
        title: 'Wrong role',
        body: 'Parent identities must not submit Child security alerts.',
      }),
      'PERMISSION_DENIED',
    )

    assertCallableError(
      await callCallable('reportChildSecurityAlert', unpairedChild.token, {
        type: 'integration_alert_unpaired',
        severity: 'warning',
        title: 'Unpaired device',
        body: 'This should not be routed.',
      }),
      'FAILED_PRECONDITION',
    )

    const result = assertCallableSuccess(
      await callCallable('reportChildSecurityAlert', childA.token, {
        type: 'integration_permission_revoked',
        severity: 'critical',
        title: 'Protection permission revoked',
        body: 'Integration test security alert.',
      }),
    )
    assert.equal(result.accepted, true)

    const notificationSnap = await db.collection('parent_notifications')
      .where('childUid', '==', childA.uid)
      .where('type', '==', 'integration_permission_revoked')
      .get()

    assert.equal(notificationSnap.size, 1)
    const notification = notificationSnap.docs[0].data()
    assert.equal(notification.parentUid, parentA.uid)
    assert.equal(notification.severity, 'critical')
    assert.equal(notification.read, false)
  })
})
