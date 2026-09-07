import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { createPairingCode, listenChildren, listenPairingCode, listenParentNotifications, listenPendingTimeRequests, markParentNotificationRead } from '../services/dashboardApi'
import { useAuth } from '../services/authContext'

const STALE_MS = 2 * 60 * 1000
const OFFLINE_MS = 5 * 60 * 1000
const toMs = (ts) => ts?.toDate?.()?.getTime?.() || 0
const getPresence = (child) => {
  const age = Date.now() - toMs(child.lastHeartbeatAt)
  if (!child.lastHeartbeatAt) return 'OFFLINE'
  if (age > OFFLINE_MS) return 'OFFLINE'
  if (age > STALE_MS) return 'STALE'
  return 'ONLINE'
}

export default function DashboardPage() {
  const { user, devBypass } = useAuth()
  const parentUid = user?.uid || null
  const [children, setChildren] = useState([])
  const [requests, setRequests] = useState([])
  const [notifications, setNotifications] = useState([])
  const [pairing, setPairing] = useState(null)
  const [pairingBusy, setPairingBusy] = useState(false)
  const [pairingError, setPairingError] = useState('')

  useEffect(() => {
    const unsubChildren = listenChildren(devBypass ? null : parentUid, setChildren, console.error)
    return () => unsubChildren()
  }, [parentUid, devBypass])

  const childUids = useMemo(() => children.map((c) => c.id), [children])
  useEffect(() => {
    const unsubRequests = listenPendingTimeRequests(devBypass ? [] : childUids, setRequests, console.error)
    return () => unsubRequests()
  }, [childUids, devBypass])

  useEffect(() => {
    const unsub = listenParentNotifications(parentUid, setNotifications, console.error)
    return () => unsub()
  }, [parentUid])

  useEffect(() => {
    if (!pairing?.code) return () => {}
    const unsub = listenPairingCode(pairing.code, setPairing, console.error)
    return () => unsub()
  }, [pairing?.code])

  const activeCount = useMemo(() => children.filter((c) => c.monitoringActive).length, [children])
  const unread = useMemo(() => notifications.filter((n) => !n.read).length, [notifications])

  const onGenerateCode = async () => {
    if (!parentUid) return
    setPairingBusy(true)
    setPairingError('')
    try {
      const created = await createPairingCode(parentUid)
      setPairing({ ...created, status: 'pending' })
    } catch (error) {
      setPairingError(error?.message || 'Could not create a pairing code. Please try again.')
    } finally {
      setPairingBusy(false)
    }
  }

  const expiresAt = pairing?.expiresAt?.toDate?.() || pairing?.expiresAt
  const expired = expiresAt ? expiresAt.getTime() < Date.now() : false

  return <div><header className="page-header"><div><span className="eyebrow">Family overview</span><h1>Good to see you</h1><p>Monitor protection health and respond to what needs your attention.</p></div><Link className="button-link" to="/children">View all devices</Link></header>
    <div className="dashboard-layout"><div>
      <section className="grid" aria-label="Family summary"><div className="card metric"><span className="metric-icon blue">◇</span><h3>Connected devices</h3><p>{children.length}</p><small>Paired to your account</small></div><div className="card metric"><span className="metric-icon green">●</span><h3>Protection active</h3><p>{activeCount}</p><small>Reporting normally</small></div><div className="card metric"><span className="metric-icon amber">◷</span><h3>Time requests</h3><p>{requests.length}</p><small>Waiting for review</small></div><div className="card metric"><span className="metric-icon red">!</span><h3>Unread alerts</h3><p>{unread}</p><small>Recent activity</small></div></section>
      <section className="card"><div className="section-heading"><div><h2>Device status</h2><p>Latest protection and connection signals.</p></div><Link to="/children">Manage devices</Link></div>{children.length === 0 ? <div className="empty-state"><span>◇</span><h3>No devices connected</h3><p>Generate a pairing code below to connect a Child device.</p></div> : children.map((child) => { const presence = getPresence(child); return <Link className="device-row" to={`/children/${child.id}`} key={child.id}><span className="device-avatar">{(child.deviceName || 'D')[0].toUpperCase()}</span><span className="device-main"><strong>{child.deviceName || 'Child device'}</strong><small>{child.platform || 'Android'} · {child.batteryLevel ?? '—'}% battery</small></span><span className={`badge ${presence === 'ONLINE' ? 'active' : presence === 'STALE' ? 'warn-badge' : 'offline'}`}>{presence}</span><span className="device-time">{child.lastHeartbeatAt?.toDate?.()?.toLocaleString?.() || 'No heartbeat yet'}</span><span aria-hidden="true">›</span></Link> })}</section>
      <section className="card pairing-card"><div><span className="eyebrow">Connect a device</span><h2>Add a Child phone</h2><p>Create a secure, temporary code and enter it in the Child app.</p></div><button onClick={onGenerateCode} disabled={!parentUid || pairingBusy}>{pairingBusy ? 'Generating…' : 'Generate pairing code'}</button>{pairingError && <div className="alert error" role="alert">{pairingError}</div>}{pairing && <div className="pairing-code" aria-live="polite"><div><small>Pairing code</small><strong>{pairing.code}</strong></div><span className={`badge ${expired ? 'danger-badge' : 'active'}`}>{expired ? 'Expired' : pairing.status || 'Pending'}</span><button className="secondary compact" onClick={() => navigator.clipboard?.writeText(pairing.code)}>Copy</button></div>}</section>
    </div><aside className="card notifications"><div className="section-heading"><div><h2>Activity alerts</h2><p>Important updates from your devices.</p></div><span className="badge offline">{unread} unread</span></div>{notifications.length === 0 ? <div className="empty-state compact"><span>✓</span><h3>All clear</h3><p>No recent notifications.</p></div> : notifications.map((n) => <article key={n.id} className={`notif-item ${n.read ? 'read' : ''}`}><div className="row wrap"><b>{n.title || 'Device update'}</b><span className={`badge ${n.severity === 'critical' ? 'danger-badge' : n.severity === 'warning' ? 'warn-badge' : 'offline'}`}>{n.severity || 'info'}</span></div><p>{n.body}</p><p className="muted">{n.createdAt?.toDate?.()?.toLocaleString?.() || 'Just now'}</p>{!n.read && <button className="secondary compact" onClick={() => markParentNotificationRead(n.id)}>Mark as read</button>}</article>)}</aside></div>
  </div>
}
