import { after, before, beforeEach, test } from 'node:test'
import { readFile } from 'node:fs/promises'
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing'
import {
  Timestamp,
  doc,
  setDoc,
  updateDoc,
} from 'firebase/firestore'

const PROJECT_ID = 'demo-digital-parenting-fcm-rules'
let testEnv

const parentDb = (uid) => testEnv.authenticatedContext(uid, {
  email: `${uid}@example.test`,
  email_verified: true,
  firebase: {
    sign_in_provider: 'password',
    identities: { email: [`${uid}@example.test`] },
  },
}).firestore()

const childDb = (uid) => testEnv.authenticatedContext(uid, {
  provider_id: 'anonymous',
  firebase: {
    sign_in_provider: 'anonymous',
    identities: {},
  },
}).firestore()

async function seedChildren() {
  const now = Timestamp.now()
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore()
    await Promise.all([
      setDoc(doc(db, 'children', 'childA'), {
        parentUid: 'parentA',
        paired: true,
        pairingCode: '111111',
        pairedAt: now,
        updatedAt: now,
        deviceName: 'Child A',
        platform: 'android',
      }),
      setDoc(doc(db, 'children', 'childB'), {
        parentUid: 'parentB',
        paired: true,
        pairingCode: '222222',
        pairedAt: now,
        updatedAt: now,
        deviceName: 'Child B',
        platform: 'android',
      }),
    ])
  })
}

before(async () => {
  const rules = await readFile(new URL('../../firestore.rules', import.meta.url), 'utf8')
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules },
  })
})

beforeEach(async () => {
  await testEnv.clearFirestore()
  await seedChildren()
})

after(async () => {
  await testEnv.cleanup()
})

test('paired Child can update only its own FCM token metadata', async () => {
  const now = Timestamp.now()
  const childA = childDb('childA')

  await assertSucceeds(updateDoc(doc(childA, 'children', 'childA'), {
    fcmToken: 'token-child-a',
    fcmTokenUpdatedAt: now,
  }))

  await assertFails(updateDoc(doc(childA, 'children', 'childB'), {
    fcmToken: 'token-stolen-path',
    fcmTokenUpdatedAt: now,
  }))

  await assertFails(updateDoc(doc(childA, 'children', 'childA'), {
    fcmToken: 'token-with-ownership-tamper',
    fcmTokenUpdatedAt: now,
    parentUid: 'parentB',
  }))
})

test('Parent cannot overwrite Child-owned FCM token metadata', async () => {
  await assertFails(updateDoc(doc(parentDb('parentA'), 'children', 'childA'), {
    fcmToken: 'parent-written-token',
    fcmTokenUpdatedAt: Timestamp.now(),
  }))
})
