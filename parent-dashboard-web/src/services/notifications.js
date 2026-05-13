import { doc, serverTimestamp, setDoc } from 'firebase/firestore'
import { getToken, isSupported } from 'firebase/messaging'
import { auth, db, getWebMessaging } from './firebase'
import { logParentFirebase } from './logger'

export const registerBrowserNotifications = async () => {
  if (!('Notification' in window)) {
    return { status: 'unsupported', message: 'Notifications are not supported in this browser.' }
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return { status: 'blocked', message: `Notification permission is ${permission}.` }
  }

  const supported = await isSupported()
  if (!supported) {
    return { status: 'unsupported', message: 'Firebase Messaging is not supported in this browser.' }
  }

  const messaging = await getWebMessaging()
  if (!messaging) {
    return { status: 'error', message: 'Messaging could not be initialized.' }
  }

  const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY
  if (!vapidKey) {
    return { status: 'missing_vapid', message: 'VAPID key is missing (set VITE_FIREBASE_VAPID_KEY).' }
  }

  const token = await getToken(messaging, { vapidKey })
  if (!token) {
    return { status: 'missing_token', message: 'No browser FCM token generated.' }
  }

  const parentUid = auth.currentUser?.uid
  if (!parentUid) {
    return { status: 'auth_missing', message: 'Sign in before registering notifications.' }
  }

  await setDoc(doc(db, 'parents', parentUid), {
    fcmToken: token,
    fcmTokenUpdatedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  }, { merge: true })

  logParentFirebase('Parent FCM token stored', { parentUid })
  return { status: 'ready', message: 'Notifications enabled for this browser.' }
}
