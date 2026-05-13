const admin = require('firebase-admin')
const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore')
const { onSchedule } = require('firebase-functions/v2/scheduler')
const { logger } = require('firebase-functions')

admin.initializeApp()

const db = admin.firestore()

exports.cleanExpiredPairingCodes = onSchedule('every 5 minutes', async () => {
  const now = admin.firestore.Timestamp.now()
  const snapshot = await db.collection('pairing_codes')
    .where('status', '==', 'pending')
    .where('expiresAt', '<', now)
    .get()

  if (snapshot.empty) {
    logger.info('No expired pending pairing codes found.')
    return
  }

  const batch = db.batch()
  snapshot.docs.forEach((doc) => {
    batch.update(doc.ref, {
      status: 'expired',
      expiredAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })
  })

  await batch.commit()
  logger.info('Expired pairing codes marked.', { count: snapshot.size })
})

exports.onCommandCreated = onDocumentCreated('children/{childUid}/commands/{commandId}', async (event) => {
  const data = event.data?.data() || {}
  const { childUid, commandId } = event.params

  logger.info('Command created', {
    childUid,
    commandId,
    type: data.type || null,
    appPackage: data.appPackage || null,
  })

  await db.collection('command_audit').add({
    childUid,
    commandId,
    type: data.type || null,
    appPackage: data.appPackage || null,
    createdAt: data.createdAt || admin.firestore.FieldValue.serverTimestamp(),
    source: 'parent_dashboard',
    auditedAt: admin.firestore.FieldValue.serverTimestamp(),
  })
})

exports.onTimeRequestCreated = onDocumentCreated('time_requests/{requestId}', async (event) => {
  const request = event.data?.data() || {}
  const requestId = event.params.requestId
  const childUid = request.childUid

  if (!childUid) {
    logger.warn('time_requests doc missing childUid', { requestId })
    return
  }

  const childSnap = await db.collection('children').doc(childUid).get()
  const child = childSnap.data() || {}
  const parentUid = child.parentUid

  if (!parentUid) {
    logger.warn('No parentUid found for child during request notification', { requestId, childUid })
    return
  }

  const parentSnap = await db.collection('parents').doc(parentUid).get()
  const parentToken = parentSnap.get('fcmToken')

  if (!parentToken) {
    logger.warn('Parent FCM token missing for time request notification', { requestId, parentUid, childUid })
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

  logger.info('Parent time request notification sent', { requestId, parentUid, childUid })
})

exports.onTimeRequestResolved = onDocumentUpdated('time_requests/{requestId}', async (event) => {
  const before = event.data?.before?.data() || {}
  const after = event.data?.after?.data() || {}
  const requestId = event.params.requestId

  const movedToApproved = before.status === 'pending' && after.status === 'approved'
  const movedToDenied = before.status === 'pending' && after.status === 'denied'

  if (!movedToApproved && !movedToDenied) return

  const childUid = after.childUid
  if (!childUid) {
    logger.warn('Resolved time request missing childUid', { requestId })
    return
  }

  const childSnap = await db.collection('children').doc(childUid).get()
  const token = childSnap.get('fcmToken')

  if (!token) {
    logger.warn('Child FCM token missing for request resolution', { requestId, childUid, status: after.status })
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

  logger.info('Child request resolution notification sent', { requestId, childUid, status: after.status })
})
