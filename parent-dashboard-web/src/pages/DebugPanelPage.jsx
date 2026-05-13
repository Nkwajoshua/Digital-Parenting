import { useEffect, useMemo, useState } from 'react'
import { listenChildren, listenPendingTimeRequests, listenRecentCommands } from '../services/dashboardApi'

export default function DebugPanelPage() {
  const [children, setChildren] = useState([])
  const [pendingRequests, setPendingRequests] = useState([])
  const [latestCommand, setLatestCommand] = useState(null)
  const [childrenListenerStatus, setChildrenListenerStatus] = useState('connecting')
  const [requestListenerStatus, setRequestListenerStatus] = useState('connecting')
  const [commandListenerStatus, setCommandListenerStatus] = useState('idle')

  const selectedChildUid = children[0]?.id || ''

  useEffect(() => {
    const unsubChildren = listenChildren((rows) => {
      setChildren(rows)
      setChildrenListenerStatus('connected')
    }, () => setChildrenListenerStatus('error'))

    const unsubRequests = listenPendingTimeRequests((rows) => {
      setPendingRequests(rows)
      setRequestListenerStatus('connected')
    }, () => setRequestListenerStatus('error'))

    return () => { unsubChildren(); unsubRequests() }
  }, [])

  useEffect(() => {
    if (!selectedChildUid) return () => {}
    setCommandListenerStatus('connecting')
    const unsubCommands = listenRecentCommands(selectedChildUid, (rows) => {
      setLatestCommand(rows[0] || null)
      setCommandListenerStatus('connected')
    }, () => setCommandListenerStatus('error'))
    return () => unsubCommands()
  }, [selectedChildUid])

  const firebaseConfigStatus = useMemo(() => (import.meta.env.VITE_FIREBASE_PROJECT_ID ? 'configured' : 'missing env values'), [])
  const firestoreConnectionStatus = useMemo(() => (childrenListenerStatus === 'connected' || requestListenerStatus === 'connected' ? 'connected' : 'not connected'), [childrenListenerStatus, requestListenerStatus])

  return <div>
    <h1>Debug Panel (Development)</h1>
    <div className="card"><p><b>Firebase config status:</b> {firebaseConfigStatus}</p></div>
    <div className="card"><p><b>Firestore connection status:</b> {firestoreConnectionStatus}</p></div>
    <div className="card"><p><b>children listener status:</b> {childrenListenerStatus}</p></div>
    <div className="card"><p><b>pending requests listener status:</b> {requestListenerStatus}</p></div>
    <div className="card"><p><b>selected child UID:</b> {selectedChildUid || 'None selected'}</p></div>
    <div className="card"><p><b>latest command status:</b> {latestCommand?.status || 'N/A'}</p></div>
    <div className="card"><p><b>pending requests loaded:</b> {pendingRequests.length}</p></div>
    <div className="card"><p><b>command listener status:</b> {commandListenerStatus}</p></div>
  </div>
}
