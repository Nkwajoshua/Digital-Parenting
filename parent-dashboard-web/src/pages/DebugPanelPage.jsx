import { useMemo } from 'react'

export default function DebugPanelPage({ childUid, firebaseStatus, latestCommand, latestRequest, latestSync }) {
  const syncedText = useMemo(() => latestSync?.toDate?.()?.toLocaleString?.() || 'N/A', [latestSync])

  return <div>
    <h1>Debug Panel (Development)</h1>
    <div className="card"><p><b>Current childUid:</b> {childUid || 'None selected'}</p></div>
    <div className="card"><p><b>Firebase status:</b> {firebaseStatus}</p></div>
    <div className="card"><p><b>Latest command:</b> {latestCommand ? `${latestCommand.type} (${latestCommand.status})` : 'N/A'}</p></div>
    <div className="card"><p><b>Latest request:</b> {latestRequest ? `${latestRequest.appName || 'app'} (${latestRequest.status})` : 'N/A'}</p></div>
    <div className="card"><p><b>Latest sync timestamp:</b> {syncedText}</p></div>
  </div>
}
