<!-- Engineered by uncoalesced -->
# Project Impart: manifesto
### The Doctrine of the Digital Panic Button

---

## 1. What Impart Is

Project Impart is a closed-loop, high-latency-intolerant paging system built for a trusted, tight-knit group of devices. It is not a messaging app. It is not a social network. It is a **summoning mechanism**, a digital analog to pulling a fire alarm, except the alarm rings in someone's pocket no matter what they were doing, no matter what their phone's settings say, no matter how deep into Doze mode their OS has gone.

When Node A pings Node B, the contract is absolute:

> Node B's device **must** wake from sleep, **must** bypass the lock screen, **must** override Do Not Disturb, and **must** sound a continuous alarm until it is physically dismissed by a human hand.

There is no "maybe." There is no "if battery optimization allows it." There is no silent failure mode. If the ping does not produce an intrusive, undeniable event on the receiving device, Impart has failed at the one thing it exists to do.

## 2. What Impart Is Not

To keep the system honest, it helps to say plainly what falls outside its philosophy:

- **Not a social feed.** There are no posts, no timelines, no likes, no read receipts for casual chat.
- **Not a general messenger.** Impart does not compete with WhatsApp, Signal, or iMessage. It has exactly one job: get a signal through when it matters.
- **Not a broadcast tool.** There is no "public" mode, no discovery, no stranger-adjacency. Every connection is deliberately, manually forged.
- **Not a convenience feature.** Impart is allowed to be heavy-handed, loud, and disruptive, because that is the entire point. A panic button that can be politely ignored is not a panic button.

## 3. The Core Directive

Identity within Impart is stripped of vanity. There are no avatars, no display customization, no social performance layer. Nodes are identified by UUIDs: cold and unambiguous. Copying someone's name, handle or even their UUID gains nothing, because an alert is trusted only when it is signed with the key stored when the two phones met. You do not "friend" someone on Impart. You **exchange trust** with them, physically, once, via the mechanism described in `03_SECURITY_PROTOCOL.md`.

This is deliberate. A system whose entire purpose is to force attention must not become a place where attention is currency. The absence of vanity metrics keeps Impart aligned with its purpose: signal, not performance.

## 4. The reliability doctrine: why V1 failed and V2 exists

Impart V1 was built on the assumption that a persistent, open connection to Firestore (snapshot listeners over WebSockets) would be sufficient to deliver a real-time signal. It was not.

| | V1 (the failure) | V2 (resurrection, the current standard) |
|---|---|---|
| **Transport** | Open Firestore snapshot listeners (WebSocket) | Encrypted SMS, delivered by the carrier and the modem |
| **Wake mechanism** | None. Relied on the process staying alive | An incoming SMS, delivered by the modem, wakes the app process on demand |
| **Doze Mode behavior** | Killed by Android Doze; listener silently disconnected | An incoming SMS raises `SMS_RECEIVED` under Doze; no app-level optimizer can throttle it |
| **OEM task killers** | Samsung/Xiaomi aggressively killed the background process; alerts silently failed | Delivery is handled by the OS's push infrastructure, not a user-space background process |
| **Identity** | Insecure 6-character IDs | UUIDs, exchanged only via out-of-band handshake |
| **UI layer** | Legacy XML Views | Jetpack Compose exclusively |
| **Failure mode** | Silent. The sender believed the alert went out; it never arrived. | Any failure must be loud and attributable. Silent failure is a critical bug, not an edge case |

The single most important lesson from V1 is this: **a background process that depends on staying alive to do its job will eventually be killed, and you will not find out until it matters.** V2 exists because Impart's job is too important to depend on a user-space process surviving Android's increasingly aggressive battery management. The OS itself, through the carrier's SMS delivery path, is the only thing trusted to wake the device.

## 5. Target Audience & Scale Philosophy

Impart is not designed to scale to millions of users. It is designed for small, deliberate, high-trust circles, originally a triad. This is a feature, not a limitation:

- **No stranger-adjacency.** You cannot discover or be discovered by anyone you have not physically exchanged a QR code with.
- **No moderation problem.** There is no content to moderate because there is no public surface area.
- **No infrastructure at all.** There is no server. A Room table of contacts and the carrier's SMS network are the whole system; Impart does not need to plan for viral growth.

If Impart ever grows beyond a triad, it should grow as a federation of small trusted circles, not as a flattened social graph. The moment it starts to resemble a social network, it has drifted from its purpose.

## 6. The Non-Negotiables

These are the laws the system does not compromise on, regardless of convenience:

1. **Ciphertext only on the wire.** The SMS body is a prefix and Tink output, nothing else. No plaintext, no metadata beyond what the carrier already sees, no server in the path that could throttle or summarize it.
2. **The alarm does not stop on its own.** Auto-timeout of the intrusion screen defeats the purpose. Dismissal must be a deliberate physical act.
3. **No plaintext leaves the device.** Google's servers relay ciphertext only. See `03_SECURITY_PROTOCOL.md`.
4. **No XML, no Fragments.** The UI layer is Compose, full stop. See `05_CODING_STANDARDS.md`.
5. **Every file is signed.** `// Engineered by uncoalesced` is not decoration. It is a provenance mark for a system whose entire premise is trust.

---

*Impart exists so that "I need you right now" is never left unread.*
