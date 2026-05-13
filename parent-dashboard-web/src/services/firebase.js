import { getApp, getApps, initializeApp } from 'firebase/app'
import { getAuth, onAuthStateChanged } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'
import { getMessaging, isSupported } from 'firebase/messaging'
import { logParentFirebase } from './logger'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

const requiredEnv = ['apiKey', 'authDomain', 'projectId', 'appId']
const missingEnv = requiredEnv.filter((key) => !firebaseConfig[key])
if (missingEnv.length > 0) {
  logParentFirebase('Missing Firebase env vars', { missingEnv })
}

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp()
export const db = getFirestore(app)
export const auth = getAuth(app)

let diagnosticsInitialized = false
export const runFirebaseConnectionDiagnostics = () => {
  if (diagnosticsInitialized) return
  diagnosticsInitialized = true

  logParentFirebase('Firebase app initialized', {
    appName: app.name,
    projectId: firebaseConfig.projectId,
    reusedExistingApp: getApps().length > 1,
  })

  try {
    const firestoreReady = !!db
    logParentFirebase('Firestore availability check', { firestoreReady })
  } catch (error) {
    logParentFirebase('Firestore availability check failed', { error })
  }

  onAuthStateChanged(auth, (user) => {
    logParentFirebase('Auth state changed', {
      authenticated: !!user,
      uid: user?.uid ?? null,
      isAnonymous: user?.isAnonymous ?? null,
    })
  }, (error) => {
    logParentFirebase('Auth state listener failed', { error })
  })
}


export const getWebMessaging = async () => {
  try {
    const supported = await isSupported()
    if (!supported) return null
    return getMessaging(app)
  } catch (error) {
    logParentFirebase('Messaging initialization failed', { error })
    return null
  }
}
