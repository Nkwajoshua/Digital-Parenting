import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import { createUserWithEmailAndPassword, onAuthStateChanged, signInWithEmailAndPassword, signOut } from 'firebase/auth'
import { doc, serverTimestamp, setDoc } from 'firebase/firestore'
import { auth, db } from './firebase'

const DEV_BYPASS_KEY = 'dev_parent_bypass'
const AuthContext = createContext(null)
const devBypassAvailable = import.meta.env.DEV

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [devBypass, setDevBypassState] = useState(
    devBypassAvailable && localStorage.getItem(DEV_BYPASS_KEY) === '1',
  )

  useEffect(() => onAuthStateChanged(auth, async (nextUser) => {
    setUser(nextUser)
    if (nextUser) {
      const profileRef = doc(db, 'parents', nextUser.uid)
      await setDoc(profileRef, {
        uid: nextUser.uid,
        email: nextUser.email || null,
        updatedAt: serverTimestamp(),
      }, { merge: true })
      await setDoc(profileRef, { createdAt: serverTimestamp() }, { merge: true })
    }
    setLoading(false)
  }), [])

  const value = useMemo(() => ({
    user,
    loading,
    devBypass,
    setDevBypass: (enabled) => {
      if (!devBypassAvailable) {
        setDevBypassState(false)
        localStorage.removeItem(DEV_BYPASS_KEY)
        return
      }

      setDevBypassState(enabled)
      if (enabled) localStorage.setItem(DEV_BYPASS_KEY, '1')
      else localStorage.removeItem(DEV_BYPASS_KEY)
    },
    signIn: (email, password) => signInWithEmailAndPassword(auth, email, password),
    signUp: (email, password) => createUserWithEmailAndPassword(auth, email, password),
    signOutUser: () => signOut(auth),
  }), [user, loading, devBypass])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
