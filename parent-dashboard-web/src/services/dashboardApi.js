import { Timestamp, collection, doc, limit, onSnapshot, orderBy, query, updateDoc, where } from 'firebase/firestore'
import { httpsCallable } from 'firebase/functions'
import { db, functions } from './firebase'
import { logParentChildren, logParentCommand, logParentRequest } from './logger'

export const ALERT_SEVERITY = Object.freeze({
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'critical',
})

const createPairingCodeCallable = httpsCallable(functions, 'createPairingCode')
const sendCommandCallable = httpsCallable(functions, 'sendCommand')
const resolveTimeRequestCallable = httpsCallable(functions, 'resolveTimeRequest')

const safeSnapshotListener = (tagLogger, sourceName, q, callback, onError) => onSnapshot(q, (snap) => {
  if ('docs' in snap) callback(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
  else callback(snap.exists() ? { id: snap.id, ...snap.data() } : null)
  tagLogger(`${sourceName} snapshot updated`, { count: snap.size })
}, (error) => { tagLogger(`${sourceName} listener failed`, { code: error.code, message: error.message }); onError?.(error) })

const timestampToMillis = (value) => value?.toMillis?.() || value?.toDate?.()?.getTime?.() || (typeof value === 'number' ? value : 0)
const asTimestamp = (value) => {
  if (value?.toDate) return value
  if (typeof value === 'number' && Number.isFinite(value)) return Timestamp.fromMillis(value)
  return null
}
const normalizeUsageSession = (row) => ({
  ...row,
  startTime: asTimestamp(row.startTime),
  endTime: asTimestamp(row.endTime),
  duration: Number.isFinite(Number(row.durationSeconds))
    ? Math.max(0, Math.round(Number(row.durationSeconds)))
    : Math.max(0, Math.round(Number(row.duration || 0) / 1000)),
})

export const listenChildren = (parentUid, callback, onError) => {
  if (!parentUid) {
    callback([])
    return () => {}
  }

  const q = query(
    collection(db, 'children'),
    where('parentUid', '==', parentUid),
    where('paired', '==', true),
    orderBy('updatedAt', 'desc'),
  )
  return safeSnapshotListener(logParentChildren, 'children', q, callback, onError)
}

export const listenPendingTimeRequests = (childUids, callback, onError) => {
  const uniqueChildUids = [...new Set((childUids || []).filter(Boolean))]

  if (uniqueChildUids.length === 0) {
    callback([])
    return () => {}
  }

  const rowsByChild = new Map()
  const emitMergedRows = () => {
    const rows = [...rowsByChild.values()]
      .flat()
      .sort((a, b) => timestampToMillis(b.createdAt) - timestampToMillis(a.createdAt))
    callback(rows)
  }

  const unsubscribers = uniqueChildUids.map((childUid) => {
    const q = query(
      collection(db, 'time_requests'),
      where('childUid', '==', childUid),
      where('status', '==', 'pending'),
      orderBy('createdAt', 'desc'),
    )

    return safeSnapshotListener(
      logParentRequest,
      `time_requests_pending:${childUid}`,
      q,
      (rows) => {
        rowsByChild.set(childUid, rows)
        emitMergedRows()
      },
      onError,
    )
  })

  return () => unsubscribers.forEach((unsubscribe) => unsubscribe())
}

export const listenParentNotifications = (parentUid, callback, onError) => {
  if (!parentUid) return () => {}
  const q = query(collection(db, 'parent_notifications'), where('parentUid', '==', parentUid), orderBy('createdAt', 'desc'), limit(50))
  return safeSnapshotListener(logParentRequest, 'parent_notifications', q, callback, onError)
}

export const markParentNotificationRead = async (id) => updateDoc(doc(db, 'parent_notifications', id), { read: true })

export const createPairingCode = async (parentUid) => {
  if (!parentUid) throw new Error('Parent authentication is required')
  const result = await createPairingCodeCallable({})
  const code = result.data?.code
  const expiresAtMillis = result.data?.expiresAtMillis
  if (!code || !expiresAtMillis) throw new Error('Pairing service returned an invalid response')
  return { code, expiresAt: new Date(expiresAtMillis) }
}

export const listenPairingCode = (code, callback, onError) => safeSnapshotListener(logParentChildren, 'pairing_code', doc(db, 'pairing_codes', code), callback, onError)

export const listenRecentCommands = (childUid, callback, onError) => safeSnapshotListener(logParentCommand, 'commands', query(collection(db, 'children', childUid, 'commands'), orderBy('createdAt', 'desc'), limit(10)), callback, onError)

const sendCommand = async (childUid, type, payload) => {
  const result = await sendCommandCallable({ childUid, type, ...payload })
  return result.data
}

export const sendBlockAppCommand = (_parentUid, childUid, appPackage, appName) => sendCommand(
  childUid,
  'block_app',
  { appPackage, appName, reason: 'Blocked by parent' },
)

export const sendUnblockAppCommand = (_parentUid, childUid, appPackage, appName) => sendCommand(
  childUid,
  'unblock_app',
  { appPackage, appName },
)

export const sendSetLimitCommand = (_parentUid, childUid, appPackage, appName, maxMinutes) => sendCommand(
  childUid,
  'set_limit',
  { appPackage, appName, maxMinutes, enabled: true },
)

export const approveTimeRequest = async (requestId, approvedMinutes) => {
  const result = await resolveTimeRequestCallable({ requestId, action: 'approve', approvedMinutes })
  return result.data
}

export const denyTimeRequest = async (requestId) => {
  const result = await resolveTimeRequestCallable({ requestId, action: 'deny' })
  return result.data
}

export const listenRecentUsageSessions = (childUid, callback, onError) => safeSnapshotListener(
  logParentChildren,
  'usage_sessions',
  query(collection(db, 'usage_sessions', childUid, 'sessions'), orderBy('startTime', 'desc'), limit(10)),
  (rows) => callback(rows.map(normalizeUsageSession)),
  onError,
)
