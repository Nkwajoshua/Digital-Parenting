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

  return <div><header className="page-header"><div><span className="eyebrow">Devices</span><h1>Child devices</h1><p>See protection health, connectivity, and device details at a glance.</p></div><span className="badge offline">{rows.length} connected</span></header>
    <div className="presence-legend"><span><i className="dot online" />Online: heartbeat within 2 minutes</span><span><i className="dot stale" />Stale: 2–5 minutes</span><span><i className="dot offline-dot" />Offline: over 5 minutes</span></div>
    {loading && <div className="device-grid" aria-label="Loading devices"><div className="card skeleton-card" /><div className="card skeleton-card" /></div>}
    {!loading && error && <div className="alert error" role="alert"><strong>We couldn’t load your devices.</strong><span>{error}</span></div>}
    {!loading && !error && rows.length === 0 && <div className="card empty-state"><span>◇</span><h2>No Child devices yet</h2><p>Go to Overview and generate a pairing code to connect the first device.</p><Link className="button-link" to="/">Go to overview</Link></div>}
    <section className="device-grid">{rows.map((child) => <article className="card device-card" key={child.id}>
      <div className="device-card-top"><span className="device-avatar large">{(child.deviceName || 'D')[0].toUpperCase()}</span><div><h2>{child.deviceName || 'Child device'}</h2><p>{child.platform || 'Android'} · {child.appVersion ? `Version ${child.appVersion}` : 'Version unavailable'}</p></div><span className={`badge ${child.presence === 'ONLINE' ? 'active' : child.presence === 'STALE' ? 'warn-badge' : 'offline'}`}>{child.presence}</span></div>
      <div className="device-stat-grid"><div><small>Protection</small><strong>{child.monitoringActive ? 'Active' : 'Inactive'}</strong></div><div><small>Battery</small><strong>{typeof child.batteryLevel === 'number' ? `${child.batteryLevel}%` : '—'} {child.charging ? '⚡' : ''}</strong></div><div><small>Last heartbeat</small><strong>{child.heartbeat?.toLocaleTimeString?.([], { hour: '2-digit', minute: '2-digit' }) || 'Never'}</strong></div></div>
      <p className="device-updated">Last updated {child.updated?.toLocaleString?.() || 'not yet available'}</p><Link className="button-link wide" to={`/children/${child.id}`}>Open device details <span aria-hidden="true">→</span></Link>
    </article>)}</section>
  </div>
}
