import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { createPairingCode, listenChildren, listenPairingCode, listenPendingTimeRequests } from '../services/dashboardApi'
import { useAuth } from '../services/authContext'

export default function DashboardPage() {
  const { user, devBypass } = useAuth()
  const parentUid = user?.uid || null
  const [children, setChildren] = useState([])
  const [requests, setRequests] = useState([])
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
    if (!pairing?.code) return () => {}
    const unsub = listenPairingCode(pairing.code, setPairing, console.error)
    return () => unsub()
  }, [pairing?.code])

  const activeCount = useMemo(() => children.filter((c) => c.monitoringActive).length, [children])
  const unpaired = useMemo(() => children.filter((c) => !c.parentUid), [children])

  const onGenerateCode = async () => {
    if (!parentUid) return
    const created = await createPairingCode(parentUid)
    setPairing({ ...created, status: 'pending' })
  }

  const expiresAt = pairing?.expiresAt?.toDate?.() || pairing?.expiresAt
  const expired = expiresAt ? expiresAt.getTime() < Date.now() : false

  return <div>
    <h1>Dashboard Home</h1>
    <section className="grid"><div className="card"><h3>Total Child Devices</h3><p>{children.length}</p></div><div className="card"><h3>Online / Active</h3><p>{activeCount}</p></div><div className="card"><h3>Pending Time Requests</h3><p>{requests.length}</p></div><div className="card"><h3>Recent Activity</h3><p>{children[0]?.deviceName ? `${children[0].deviceName} synced recently` : 'No activity yet'}</p></div></section>
    <div className="card"><h3>Add Child Device</h3><button onClick={onGenerateCode} disabled={!parentUid}>Generate 6-digit Pairing Code</button>{pairing && <div><p><b>Code:</b> <code>{pairing.code}</code> <button onClick={() => navigator.clipboard?.writeText(pairing.code)}>Copy code</button></p><p><b>Status:</b> {expired ? 'expired' : pairing.status || 'pending'}</p><p><b>Expires:</b> {expiresAt?.toLocaleString?.() || 'N/A'}</p></div>}</div>
    {devBypass && <div className="card dev-only"><h3>Unpaired Test Devices (Dev-only)</h3><p>{unpaired.length} unpaired devices visible due to dev bypass.</p></div>}
    <div className="card"><h3>Quick Links</h3><Link to="/children">Manage child devices</Link><br /><Link to="/debug">Open debug panel</Link></div>
  </div>
}
