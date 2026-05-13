import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listenChildren } from '../services/dashboardApi'
import { logParentChildren } from '../services/logger'

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

  return <div>
    <h1>Child Devices</h1>
    {loading && <p>Loading devices...</p>}
    {!loading && error && <p className="danger">Firebase error: {error}</p>}
    {!loading && !error && children.length === 0 && <p>No children found.</p>}
    {children.map((child) => (
      <div className="card" key={child.id}>
        <h3>{child.deviceName || child.id}</h3>
        <p>Platform: {child.platform || 'unknown'}</p>
        <p>Status: <span className={child.monitoringActive ? 'badge active' : 'badge offline'}>{child.monitoringActive ? 'Active' : 'Offline'}</span></p>
        <p>Last Sync: {child.updatedAt?.toDate?.()?.toLocaleString?.() || 'N/A'}</p>
        <p>UID: {child.id}</p>
        <Link to={`/children/${child.id}`}>View Details</Link>
      </div>
    ))}
  </div>
}
