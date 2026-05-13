import { addDoc, collection, doc, limit, onSnapshot, orderBy, query, serverTimestamp, setDoc, updateDoc, where } from 'firebase/firestore'
import { db } from './firebase'
import { logParentChildren, logParentCommand, logParentRequest } from './logger'

export const ALERT_SEVERITY = Object.freeze({
  INFO: 'info',
  WARNING: 'warning',
  CRITICAL: 'critical',
})

const safeSnapshotListener = (tagLogger, sourceName, q, callback, onError) => onSnapshot(q, (snap) => {
  if ('docs' in snap) callback(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
  else callback(snap.exists() ? { id: snap.id, ...snap.data() } : null)
  tagLogger(`${sourceName} snapshot updated`, { count: snap.size })
}, (error) => { tagLogger(`${sourceName} listener failed`, { code: error.code, message: error.message }); onError?.(error) })

export const listenChildren = (parentUid, callback, onError) => {
  const q = parentUid
    ? query(collection(db, 'children'), where('parentUid', '==', parentUid), orderBy('updatedAt', 'desc'))
    : query(collection(db, 'children'), orderBy('updatedAt', 'desc'))
  return safeSnapshotListener(logParentChildren, 'children', q, callback, onError)
}

export const listenPendingTimeRequests = (childUids, callback, onError) => {
  const q = query(collection(db, 'time_requests'), where('status', '==', 'pending'), orderBy('createdAt', 'desc'))
  return safeSnapshotListener(logParentRequest, 'time_requests_pending', q, (rows) => callback(childUids?.length ? rows.filter((r) => childUids.includes(r.childUid)) : rows), onError)
}

export const listenParentNotifications = (parentUid, callback, onError) => {
  if (!parentUid) return () => {}
  const q = query(collection(db, 'parent_notifications'), where('parentUid', '==', parentUid), orderBy('createdAt', 'desc'), limit(50))
  return safeSnapshotListener(logParentRequest, 'parent_notifications', q, callback, onError)
}

export const markParentNotificationRead = async (id) => updateDoc(doc(db, 'parent_notifications', id), { read: true })

export const createPairingCode = async (parentUid) => {
  const code = String(Math.floor(100000 + Math.random() * 900000))
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000)
  await setDoc(doc(db, 'pairing_codes', code), {
    code, parentUid, status: 'pending', createdAt: serverTimestamp(), expiresAt, usedByChildUid: null, usedAt: null,
  })
  return { code, expiresAt }
}

export const listenPairingCode = (code, callback, onError) => safeSnapshotListener(logParentChildren, 'pairing_code', doc(db, 'pairing_codes', code), callback, onError)

export const listenRecentCommands = (childUid, callback, onError) => safeSnapshotListener(logParentCommand, 'commands', query(collection(db, 'children', childUid, 'commands'), orderBy('createdAt', 'desc'), limit(10)), callback, onError)
export const sendBlockAppCommand = (childUid, appPackage, appName) => addDoc(collection(db, 'children', childUid, 'commands'), { type: 'block_app', appPackage, appName, reason: 'Blocked by parent', status: 'pending', severity: ALERT_SEVERITY.WARNING, createdAt: serverTimestamp() })
export const sendUnblockAppCommand = (childUid, appPackage, appName) => addDoc(collection(db, 'children', childUid, 'commands'), { type: 'unblock_app', appPackage, appName, status: 'pending', severity: ALERT_SEVERITY.INFO, createdAt: serverTimestamp() })
export const sendSetLimitCommand = (childUid, appPackage, appName, maxMinutes) => addDoc(collection(db, 'children', childUid, 'commands'), { type: 'set_limit', appPackage, appName, maxMinutes, enabled: true, status: 'pending', severity: ALERT_SEVERITY.INFO, createdAt: serverTimestamp() })
export const approveTimeRequest = async (requestId, approvedMinutes) => updateDoc(doc(db, 'time_requests', requestId), { status: 'approved', parentResponse: 'approved', approvedMinutes, resolvedAt: serverTimestamp() })
export const denyTimeRequest = async (requestId) => updateDoc(doc(db, 'time_requests', requestId), { status: 'denied', parentResponse: 'denied', approvedMinutes: 0, resolvedAt: serverTimestamp() })
export const listenRecentUsageSessions = (childUid, callback, onError) => safeSnapshotListener(logParentChildren, 'usage_sessions', query(collection(db, 'usage_sessions', childUid, 'sessions'), orderBy('startTime', 'desc'), limit(10)), callback, onError)
