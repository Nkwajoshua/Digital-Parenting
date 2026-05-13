import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { listenChildren } from '../services/dashboardApi'
import { logParentChildren } from '../services/logger'

const ONLINE_WINDOW_MS = 2 * 60 * 1000

const getUpdatedDate = (timestamp) => timestamp?.toDate?.() || null

export default function ChildrenPage() {
  const [children, setChildren] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    const unsub = listenChildren((data) => {
      setChildren(data)
      setLoading(false)
      setError('')
    }, (err) => {
      setLoading(false)
      setError(err?.message || 'Firebase listener failed')
      logParentChildren('children listener error state', { err })
    })

    return () => unsub()
  }, [])

  const now = Date.now()
  const rows = useMemo(() => children.map((child) => {
    const updated = getUpdatedDate(child.updatedAt)
    const online = updated ? now - updated.getTime() <= ONLINE_WINDOW_MS : false
    return { ...child, updated, online }
  }), [children, now])

  return <div>
    <h1>Child Devices</h1>
    <p className="muted">Online is based on last sync freshness (within 2 minutes).</p>
    {loading && <p>Loading devices...</p>}
    {!loading && error && <p className="danger">Listener error: {error}</p>}
    {!loading && !error && rows.length === 0 && <div className="card"><p>No child devices found yet. Connect a child app and sync again.</p></div>}
    {rows.map((child) => (
      <div className="card" key={child.id}>
        <h3>{child.deviceName || child.id}</h3>
        <p>Platform: {child.platform || 'unknown'}</p>
        <p>Monitoring: <span className={child.monitoringActive ? 'badge active' : 'badge offline'}>{child.monitoringActive ? 'Active' : 'Inactive'}</span></p>
        <p>Connection: <span className={child.online ? 'badge active' : 'badge offline'}>{child.online ? 'Online' : 'Offline'}</span></p>
        <p>Updated: {child.updated?.toLocaleString?.() || 'N/A'}</p>
        <p>UID: <code>{child.id}</code></p>
        <Link to={`/children/${child.id}`}>Open Details</Link>
      </div>
    ))}
  </div>
}
