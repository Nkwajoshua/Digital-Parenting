import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listenChildren } from '../services/dashboardApi'
import { useAuth } from '../services/authContext'
import { logParentChildren } from '../services/logger'

const STALE_MS = 2 * 60 * 1000
const OFFLINE_MS = 5 * 60 * 1000

const getUpdatedDate = (timestamp) => timestamp?.toDate?.() || null
const getHeartbeatDate = (timestamp) => timestamp?.toDate?.() || null

export default function ChildrenPage() {
  const { user, devBypass } = useAuth()
  const [children, setChildren] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const unsub = listenChildren(devBypass ? null : user?.uid, (data) => {
      setChildren(data)
      setLoading(false)
      setError('')
    }, (err) => {
      setLoading(false)
      setError(err?.message || 'Firebase listener failed')
      logParentChildren('children listener error state', { err })
    })

    return () => unsub()
  }, [user?.uid, devBypass])

  const now = Date.now()
  const rows = useMemo(() => children.map((child) => {
    const updated = getUpdatedDate(child.updatedAt)
    const heartbeat = getHeartbeatDate(child.lastHeartbeatAt)
    const age = heartbeat ? now - heartbeat.getTime() : Number.POSITIVE_INFINITY
    const presence = age <= STALE_MS ? 'ONLINE' : age <= OFFLINE_MS ? 'STALE' : 'OFFLINE'
    return { ...child, updated, heartbeat, presence }
  }), [children, now])

  return <div>
    <h1>Child Devices</h1>
    <p className="muted">Presence uses heartbeat freshness: ONLINE &lt;2m, STALE &gt;2m, OFFLINE &gt;5m.</p>
    {loading && <p>Loading devices...</p>}
    {!loading && error && <p className="danger">Listener error: {error}</p>}
    {!loading && !error && rows.length === 0 && <div className="card"><p>No child devices found yet. Connect a child app and sync again.</p></div>}
    {rows.map((child) => (
      <div className="card" key={child.id}>
        <h3>{child.deviceName || child.id}</h3>
        <p>Platform: {child.platform || 'unknown'}</p>
        <p>Monitoring: <span className={child.monitoringActive ? 'badge active' : 'badge offline'}>{child.monitoringActive ? 'Active' : 'Inactive'}</span></p>
        <p>Connection: <span className={`badge ${child.presence === 'ONLINE' ? 'active' : child.presence === 'STALE' ? 'warn-badge' : 'offline'}`}>{child.presence}</span></p><p>Latest heartbeat: {child.heartbeat?.toLocaleString?.() || 'N/A'}</p><p>Battery: {typeof child.batteryLevel === 'number' ? `${child.batteryLevel}%` : 'N/A'} {child.charging ? '(charging)' : ''}</p>
        <p>Updated: {child.updated?.toLocaleString?.() || 'N/A'}</p>
        <p>UID: <code>{child.id}</code></p>
        <Link to={`/children/${child.id}`}>Open Details</Link>
      </div>
    ))}
  </div>
}
