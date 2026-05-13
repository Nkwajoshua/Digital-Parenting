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
    const created = await createPairingCode(parentUid)
    setPairing({ ...created, status: 'pending' })
  }

  const expiresAt = pairing?.expiresAt?.toDate?.() || pairing?.expiresAt
  const expired = expiresAt ? expiresAt.getTime() < Date.now() : false

  return <div className="dashboard-layout"><div>
    <h1>Dashboard Home</h1>
    <section className="grid"><div className="card"><h3>Total Child Devices</h3><p>{children.length}</p></div><div className="card"><h3>Online / Active</h3><p>{activeCount}</p></div><div className="card"><h3>Pending Time Requests</h3><p>{requests.length}</p></div><div className="card"><h3>Unread Alerts</h3><p>{unread}</p></div></section>
    <div className="card"><h3>Child Presence</h3>{children.map((child) => <div key={child.id} className="row wrap"><span>{child.deviceName || child.id}</span><span className={`badge ${getPresence(child) === 'ONLINE' ? 'active' : getPresence(child) === 'STALE' ? 'warn-badge' : 'offline'}`}>{getPresence(child)}</span><span>{child.lastHeartbeatAt?.toDate?.()?.toLocaleString?.() || 'No heartbeat'}</span></div>)}</div>
    <div className="card"><h3>Add Child Device</h3><button onClick={onGenerateCode} disabled={!parentUid}>Generate 6-digit Pairing Code</button>{pairing && <div><p><b>Code:</b> <code>{pairing.code}</code></p><p><b>Status:</b> {expired ? 'expired' : pairing.status || 'pending'}</p></div>}</div>
    <div className="card"><h3>Quick Links</h3><Link to="/children">Manage child devices</Link><br /><Link to="/debug">Open debug panel</Link></div>
  </div>
  <aside className="card"><h3>Notifications <span className="badge offline">{unread} unread</span></h3>
    {notifications.length === 0 ? <p className="muted">No recent notifications.</p> : notifications.map((n) => <div key={n.id} className="notif-item"><div className="row wrap"><b>{n.title}</b><span className={`badge ${n.severity === 'critical' ? 'danger-badge' : n.severity === 'warning' ? 'warn-badge' : 'offline'}`}>{n.severity || 'info'}</span></div><p>{n.body}</p><p className="muted">{n.createdAt?.toDate?.()?.toLocaleString?.() || 'pending'} · {n.type}</p>{!n.read && <button onClick={() => markParentNotificationRead(n.id)}>Mark read</button>}</div>)}
  </aside></div>
}
