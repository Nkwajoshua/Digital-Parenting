const admin = require('firebase-admin')
const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore')
const { onSchedule } = require('firebase-functions/v2/scheduler')
const { logger } = require('firebase-functions')

admin.initializeApp()

const db = admin.firestore()
const MAX_BATCH_SIZE = 500

function chunkArray(items, size) {
  const chunks = []
  for (let i = 0; i < items.length; i += size) {
    chunks.push(items.slice(i, i + size))
  }
  return chunks
}

exports.cleanExpiredPairingCodes = onSchedule('every 5 minutes', async () => {
  try {
    const now = admin.firestore.Timestamp.now()
    const snapshot = await db.collection('pairing_codes')
      .where('status', '==', 'pending')
      .where('expiresAt', '<', now)
      .get()

    if (snapshot.empty) {
      logger.info('[PAIRING_CLEANUP] No expired pending pairing codes found.', { count: 0 })
      return
    }

    const chunks = chunkArray(snapshot.docs, MAX_BATCH_SIZE)
    let expiredCount = 0
    let skippedCount = 0

    for (let i = 0; i < chunks.length; i += 1) {
      const chunk = chunks[i]
      try {
        const result = await db.runTransaction(async (tx) => {
          let chunkExpired = 0
          let chunkSkipped = 0

          for (const doc of chunk) {
            const currentSnap = await tx.get(doc.ref)
            const current = currentSnap.data() || {}

            if (current.status !== 'pending') {
              chunkSkipped += 1
              continue
            }

            tx.update(doc.ref, {
              status: 'expired',
              expiredAt: admin.firestore.FieldValue.serverTimestamp(),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            })
            chunkExpired += 1
          }

          return { chunkExpired, chunkSkipped }
        })

        expiredCount += result.chunkExpired
        skippedCount += result.chunkSkipped
        logger.info('[PAIRING_CLEANUP] Chunk commit complete.', {
          chunkIndex: i,
          chunkSize: chunk.length,
          chunkExpired: result.chunkExpired,
          chunkSkipped: result.chunkSkipped,
        })
      } catch (chunkError) {
        logger.error('[PAIRING_CLEANUP] Chunk commit failed.', {
          chunkIndex: i,
          chunkSize: chunk.length,
          error: chunkError.message,
          stack: chunkError.stack,
        })
      }
    }

    logger.info('[PAIRING_CLEANUP] Expired pairing code cleanup complete.', {
      totalMatched: snapshot.size,
      expiredCount,
      skippedCount,
      chunkCount: chunks.length,
    })
  } catch (error) {
    logger.error('[PAIRING_CLEANUP] Cleanup failed.', {
      error: error.message,
      stack: error.stack,
    })
  }
})

exports.onCommandCreated = onDocumentCreated('children/{childUid}/commands/{commandId}', async (event) => {
  try {
    const data = event.data?.data() || {}
    const { childUid, commandId } = event.params

    logger.info('[COMMAND_AUDIT] Command created.', {
      childUid,
      commandId,
      parentUid: data.parentUid || null,
      type: data.type || null,
      appPackage: data.appPackage || null,
    })

    await db.collection('command_audit').add({
      childUid,
      commandId,
      parentUid: data.parentUid || null,
      type: data.type || null,
      appPackage: data.appPackage || null,
      createdAt: data.createdAt || admin.firestore.FieldValue.serverTimestamp(),
      source: 'parent_dashboard',
      auditedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    logger.info('[COMMAND_AUDIT] Audit entry written.', {
      childUid,
      commandId,
      parentUid: data.parentUid || null,
    })
  } catch (error) {
    logger.error('[COMMAND_AUDIT] Failed to audit command.', {
      childUid: event.params?.childUid || null,
      commandId: event.params?.commandId || null,
      error: error.message,
      stack: error.stack,
    })
  }
})

exports.onTimeRequestCreated = onDocumentCreated('time_requests/{requestId}', async (event) => {
  try {
    const request = event.data?.data() || {}
    const requestId = event.params.requestId
    const childUid = request.childUid

    if (!childUid) {
      logger.warn('[TIME_REQUEST] time_requests doc missing childUid.', { requestId })
      return
    }

    const childSnap = await db.collection('children').doc(childUid).get()
    const child = childSnap.data() || {}
    const parentUid = child.parentUid

    if (!parentUid) {
      logger.warn('[TIME_REQUEST] No parentUid found for child during request notification.', { requestId, childUid })
      return
    }

    const parentSnap = await db.collection('parents').doc(parentUid).get()
    const parentToken = parentSnap.get('fcmToken')

    if (!parentToken) {
      logger.warn('[FCM] Parent FCM token missing for time request notification.', { requestId, parentUid, childUid })
      return
    }

    const childName = request.childName || child.childName || child.displayName || 'Your child'

    await admin.messaging().send({
      token: parentToken,
      notification: {
        title: 'New Time Request',
        body: `${childName} requested more screen time`,
      },
      data: {
        type: 'time_request_created',
        requestId,
        childUid,
      },
    })

    logger.info('[FCM] Parent time request notification sent.', { requestId, parentUid, childUid })
  } catch (error) {
    logger.error('[TIME_REQUEST] Failed handling created time request.', {
      requestId: event.params?.requestId || null,
      childUid: event.data?.data()?.childUid || null,
      error: error.message,
      stack: error.stack,
    })
  }
})

exports.onTimeRequestResolved = onDocumentUpdated('time_requests/{requestId}', async (event) => {
  try {
    const before = event.data?.before?.data() || {}
    const after = event.data?.after?.data() || {}
    const requestId = event.params.requestId

    const movedToApproved = before.status === 'pending' && after.status === 'approved'
    const movedToDenied = before.status === 'pending' && after.status === 'denied'

    if (!movedToApproved && !movedToDenied) return

    const childUid = after.childUid
    if (!childUid) {
      logger.warn('[TIME_REQUEST] Resolved time request missing childUid.', { requestId })
      return
    }

    const childSnap = await db.collection('children').doc(childUid).get()
    const token = childSnap.get('fcmToken')

    if (!token) {
      logger.warn('[FCM] Child FCM token missing for request resolution.', { requestId, childUid, status: after.status })
      return
    }

    const body = movedToApproved ? 'Your request was approved' : 'Your request was denied'
    await admin.messaging().send({
      token,
      notification: {
        title: 'Time Request Update',
        body,
      },
      data: {
        type: 'time_request_resolved',
        requestId,
        status: after.status,
      },
    })

    logger.info('[FCM] Child request resolution notification sent.', { requestId, childUid, status: after.status })
  } catch (error) {
    logger.error('[TIME_REQUEST] Failed handling resolved time request.', {
      requestId: event.params?.requestId || null,
      childUid: event.data?.after?.data()?.childUid || null,
      error: error.message,
      stack: error.stack,
    })
  }
})
