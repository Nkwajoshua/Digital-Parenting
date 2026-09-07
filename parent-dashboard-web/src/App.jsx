import { useEffect, useState } from 'react'
import { NavLink, Route, Routes } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage'
import ChildrenPage from './pages/ChildrenPage'
import ChildDetailPage from './pages/ChildDetailPage'
import DebugPanelPage from './pages/DebugPanelPage'
import { useAuth } from './services/authContext'
import { registerBrowserNotifications } from './services/notifications'

function Brand() {
  return <div className="brand"><span className="brand-mark">DP</span><span><strong>Digital Parenting</strong><small>Family safety console</small></span></div>
}

function LoginScreen() {
  const { signIn, signUp, devBypass, setDevBypass } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState('')
  const run = async (mode) => {
    setError(''); setBusy(mode)
    try { if (mode === 'signin') await signIn(email, password); else await signUp(email, password) }
    catch (authError) { setError(authError?.message || 'Authentication failed. Please try again.') }
    finally { setBusy('') }
  }
  return <main className="auth-page">
    <section className="auth-intro"><Brand /><div className="auth-message"><span className="eyebrow light">A calmer way to stay connected</span><h1>Protect their digital world with clarity and care.</h1><p>Review device health, respond to time requests, and manage healthy app boundaries from one secure place.</p></div><small>Private by design · Parent-authorized controls</small></section>
    <section className="auth-panel"><form className="auth-card" onSubmit={(event) => { event.preventDefault(); run('signin') }}><span className="eyebrow">Parent portal</span><h2>Welcome back</h2><p className="muted">Sign in to manage your connected child devices.</p><label>Email address<input type="email" autoComplete="email" placeholder="parent@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" autoComplete="current-password" placeholder="Enter your password" value={password} onChange={(event) => setPassword(event.target.value)} minLength="6" required /></label>{error && <div className="alert error" role="alert">{error}</div>}<button className="wide" type="submit" disabled={Boolean(busy)}>{busy === 'signin' ? 'Signing in…' : 'Sign in securely'}</button><div className="auth-divider"><span>New to Digital Parenting?</span></div><button className="secondary wide" type="button" disabled={Boolean(busy)} onClick={() => run('signup')}>{busy === 'signup' ? 'Creating account…' : 'Create parent account'}</button></form>{import.meta.env.DEV && <div className="dev-card"><strong>Development tools</strong><span>Use sample data without authentication.</span><button className="ghost" type="button" onClick={() => setDevBypass(!devBypass)}>{devBypass ? 'Disable' : 'Enable'} bypass</button></div>}</section>
  </main>
}

export default function App() {
  const { user, loading, signOutUser, devBypass } = useAuth()
  const [notificationStatus, setNotificationStatus] = useState('Not enabled')
  const [menuOpen, setMenuOpen] = useState(false)
  const effectiveDevBypass = import.meta.env.DEV && devBypass
  useEffect(() => { if (typeof Notification === 'undefined') setNotificationStatus('Not supported'); else setNotificationStatus(Notification.permission === 'granted' ? 'Enabled' : 'Not enabled') }, [])
  const enableNotifications = async () => { try { const result = await registerBrowserNotifications(); setNotificationStatus(result.message) } catch (error) { setNotificationStatus(error?.message || 'Setup failed') } }
  if (loading) return <main className="loading-page" aria-live="polite"><span className="spinner" /><p>Securing your parent portal…</p></main>
  if (!user && !effectiveDevBypass) return <LoginScreen />
  return <div className="app-shell">
    <header className="mobile-header"><Brand /><button className="icon-button" aria-label="Toggle navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen(!menuOpen)}>☰</button></header>
    <aside className={`sidebar ${menuOpen ? 'open' : ''}`}><Brand /><nav className="primary-nav" aria-label="Primary navigation"><span className="nav-label">Workspace</span><NavLink to="/" end onClick={() => setMenuOpen(false)}>⌂ <span>Overview</span></NavLink><NavLink to="/children" onClick={() => setMenuOpen(false)}>◇ <span>Child devices</span></NavLink>{import.meta.env.DEV && <NavLink to="/debug" onClick={() => setMenuOpen(false)}>⚙ <span>Debug panel</span></NavLink>}</nav><div className="sidebar-spacer" /><section className="sidebar-card"><span className={`status-dot ${notificationStatus === 'Enabled' ? 'online' : ''}`} /><div><strong>Browser alerts</strong><small>{notificationStatus}</small></div>{notificationStatus !== 'Enabled' && <button className="text-button" onClick={enableNotifications}>Enable</button>}</section><div className="profile-menu"><span className="avatar">{user?.email?.[0]?.toUpperCase() || 'P'}</span><div><strong>{user?.email || 'Development parent'}</strong><small>Parent account</small></div><button className="icon-button dark" aria-label="Sign out" disabled={!user} onClick={() => user && signOutUser()}>↗</button></div></aside>
    {menuOpen && <button className="nav-scrim" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
    <main className="content"><Routes><Route path="/" element={<DashboardPage />} /><Route path="/children" element={<ChildrenPage />} /><Route path="/children/:childUid" element={<ChildDetailPage />} />{import.meta.env.DEV && <Route path="/debug" element={<DebugPanelPage />} />}</Routes></main>
  </div>
}
