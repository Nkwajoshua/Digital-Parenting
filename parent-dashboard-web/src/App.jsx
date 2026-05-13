import { Link, Route, Routes } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage'
import ChildrenPage from './pages/ChildrenPage'
import ChildDetailPage from './pages/ChildDetailPage'
import DebugPanelPage from './pages/DebugPanelPage'
import { useAuth } from './services/authContext'
import { useState } from 'react'

function LoginScreen() {
  const { signIn, signUp, devBypass, setDevBypass } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const run = async (mode) => {
    setError('')
    try { if (mode === 'signin') await signIn(email, password); else await signUp(email, password) } catch (e) { setError(e.message) }
  }

  return <main className="content"><h1>Parent Sign In</h1><div className="card"><input placeholder="Email" value={email} onChange={(e) => setEmail(e.target.value)} /><input type="password" placeholder="Password" value={password} onChange={(e) => setPassword(e.target.value)} /><div className="button-row"><button onClick={() => run('signin')}>Sign In</button><button onClick={() => run('signup')}>Create Account</button></div>{error && <p className="danger">{error}</p>}</div><div className="card dev-only"><h3>Dev-only bypass</h3><p className="muted">Use only for legacy manual testing data.</p><button onClick={() => setDevBypass(!devBypass)}>{devBypass ? 'Disable' : 'Enable'} Dev Bypass</button></div></main>
}

export default function App() {
  const { user, loading, signOutUser, devBypass } = useAuth()
  if (loading) return <main className="content"><p>Loading auth...</p></main>
  if (!user && !devBypass) return <LoginScreen />

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <h2>Parent Dashboard</h2>
        <nav>
          <Link to="/">Home</Link><Link to="/children">Child Devices</Link><Link to="/debug">Debug Panel</Link>
        </nav>
        <p className="muted">Parent UID: {user?.uid || 'DEV_BYPASS'}</p>
        <button onClick={() => user ? signOutUser() : null} disabled={!user}>Sign Out</button>
      </aside>
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/children" element={<ChildrenPage />} />
          <Route path="/children/:childUid" element={<ChildDetailPage />} />
          <Route path="/debug" element={<DebugPanelPage />} />
        </Routes>
      </main>
    </div>
  )
}
