import {
  addDoc,
  collection,
  doc,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  updateDoc,
  where,
} from 'firebase/firestore'
import { db } from './firebase'
import { logParentChildren, logParentCommand, logParentRequest } from './logger'

const safeSnapshotListener = (tagLogger, sourceName, q, callback, onError) => onSnapshot(q,
  (snap) => {
    callback(snap.docs.map((d) => ({ id: d.id, ...d.data() })))
    tagLogger(`${sourceName} snapshot updated`, { count: snap.size })
  },
  (error) => {
    tagLogger(`${sourceName} listener failed`, { code: error.code, message: error.message })
    onError?.(error)
  })

export const listenChildren = (callback, onError) => {
  const q = query(collection(db, 'children'), orderBy('updatedAt', 'desc'))
  logParentChildren('Subscribing children listener')
  return safeSnapshotListener(logParentChildren, 'children', q, callback, onError)
}

export const listenPendingTimeRequests = (callback, onError) => {
  const q = query(collection(db, 'time_requests'), where('status', '==', 'pending'), orderBy('createdAt', 'desc'))
  logParentRequest('Subscribing pending time_requests listener')
  return safeSnapshotListener(logParentRequest, 'time_requests_pending', q, callback, onError)
}

export const listenRecentCommands = (childUid, callback, onError) => {
  const q = query(collection(db, 'children', childUid, 'commands'), orderBy('createdAt', 'desc'), limit(10))
  logParentCommand('Subscribing commands listener', { childUid })
  return safeSnapshotListener(logParentCommand, 'commands', q, callback, onError)
}

export const sendBlockAppCommand = (childUid, appPackage, appName) => addDoc(collection(db, 'children', childUid, 'commands'), {
  type: 'block_app', appPackage, appName, reason: 'Blocked by parent', status: 'pending', createdAt: serverTimestamp(),
})

export const sendUnblockAppCommand = (childUid, appPackage, appName) => addDoc(collection(db, 'children', childUid, 'commands'), {
  type: 'unblock_app', appPackage, appName, status: 'pending', createdAt: serverTimestamp(),
})

export const sendSetLimitCommand = (childUid, appPackage, appName, maxMinutes) => addDoc(collection(db, 'children', childUid, 'commands'), {
  type: 'set_limit', appPackage, appName, maxMinutes, enabled: true, status: 'pending', createdAt: serverTimestamp(),
})

export const approveTimeRequest = async (requestId, approvedMinutes) => updateDoc(doc(db, 'time_requests', requestId), {
  status: 'approved',
  parentResponse: 'approved',
  approvedMinutes,
  resolvedAt: serverTimestamp(),
})

export const denyTimeRequest = async (requestId) => updateDoc(doc(db, 'time_requests', requestId), {
  status: 'denied',
  parentResponse: 'denied',
  approvedMinutes: 0,
  resolvedAt: serverTimestamp(),
})

export const listenRecentUsageSessions = (childUid, callback, onError) => {
  const q = query(collection(db, 'usage_sessions', childUid, 'sessions'), orderBy('startTime', 'desc'), limit(10))
  return safeSnapshotListener(logParentChildren, 'usage_sessions', q, callback, onError)
}
