import React, { useState } from 'react'
import { Button, Checkbox, Row, Stack, Text, TextArea, useCanvas } from 'canvas'

type Icons = Record<string, Record<string, string>>
type Weight = 'regular' | 'duotone' | 'fill'

const AMBER = '#ffb347'
const u = (n: number) => `${n}cqw`

const EXTRAS = [
  { key: 'status', label: 'Live status', blurb: 'Tile dims and says "Asleep" or "No signal" when the device is off (input state + CEC power).' },
  { key: 'backdrop', label: 'Time-of-day backdrop', blurb: 'The glow behind the tiles goes warm amber at night, cool blue by day.' },
  { key: 'idle', label: 'Idle dim + drift', blurb: 'After 2 min on Home, tiles fade and only a dim clock remains, drifting a few px a minute (burn-in safe).' },
  { key: 'last', label: 'Remember last input', blurb: 'Focus starts on whatever you used last, so Home → OK goes back.' },
  { key: 'back', label: 'Back = return', blurb: 'Pressing Back on Home jumps back to the last input (undo an accidental Home).' },
]

function Glyph({ svg, color, size }: { svg: string; color: string; size: string }) {
  return <svg viewBox="0 0 256 256" fill="currentColor" style={{ width: size, height: size, color }} dangerouslySetInnerHTML={{ __html: svg }} />
}

export default function Tiles({ data }: { data: Icons }) {
  const { submit } = useCanvas()
  const [weight, setWeight] = useState<Weight>('duotone')
  const [atvIcon, setAtvIcon] = useState('apple-logo')
  const [pcIcon, setPcIcon] = useState('game-controller')
  const [focus, setFocus] = useState(0)
  const [night, setNight] = useState(true)
  const [idlePreview, setIdlePreview] = useState(false)
  const [extras, setExtras] = useState<Record<string, boolean>>({ status: true, backdrop: true, idle: true, last: true, back: true })
  const [notes, setNotes] = useState('')

  const tiles = [
    { name: 'Apple TV', sub: 'HDMI 2', icon: atvIcon, state: 'On' },
    { name: 'Gaming PC', sub: 'HDMI 1', icon: pcIcon, state: 'Asleep' },
  ]
  const glow = extras.backdrop ? (night ? '#3a2410' : '#12233a') : '#1c2333'

  return (
    <Stack gap={12}>
      <div style={{ containerType: 'inline-size' }}>
        <div style={{
          position: 'relative', width: '100%', aspectRatio: '16 / 9', borderRadius: 10, overflow: 'hidden', border: '1px solid var(--border)',
          background: `radial-gradient(120% 90% at 50% 115%, ${glow} 0%, #07080b 62%)`, color: '#fff',
          fontFamily: '-apple-system, "SF Pro Display", system-ui, sans-serif', transition: 'background .4s ease',
        }}>
          {idlePreview ? (
            <div style={{ position: 'absolute', left: '38%', top: '40%', fontSize: u(7), fontWeight: 200, opacity: 0.22 }}>10:01</div>
          ) : (
            <>
              <div style={{ position: 'absolute', top: u(3.5), left: u(4.5), fontSize: u(2.4), fontWeight: 300, opacity: 0.85 }}>10:01</div>
              <div onClick={() => setFocus(2)} style={{ position: 'absolute', top: u(3.4), right: u(4.5), cursor: 'pointer', opacity: focus === 2 ? 1 : 0.45 }}>
                <Glyph svg={data['gear-six'][weight]} color={focus === 2 ? AMBER : '#fff'} size={u(2.8)} />
              </div>
              <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: u(3.5) }}>
                {tiles.map((t, i) => {
                  const on = focus === i
                  const asleep = extras.status && t.state !== 'On'
                  return (
                    <div key={t.name} onClick={() => setFocus(i)} style={{
                      position: 'relative', width: u(24), height: u(17), borderRadius: u(1.8), cursor: 'pointer',
                      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: u(1.1),
                      background: on ? 'rgba(255,255,255,0.13)' : 'rgba(255,255,255,0.045)',
                      border: `${u(0.18)} solid ${on ? AMBER : 'rgba(255,255,255,0.07)'}`,
                      boxShadow: on ? `0 0 ${u(3.5)} ${AMBER}44` : 'none',
                      transform: on ? 'scale(1.08)' : 'scale(1)', transition: 'all .18s ease',
                      opacity: asleep && !on ? 0.55 : 1,
                    }}>
                      <Glyph svg={data[t.icon][weight]} color={on ? AMBER : '#d5d8de'} size={u(6.2)} />
                      <div style={{ fontSize: u(1.9), fontWeight: 500, opacity: on ? 1 : 0.75 }}>{t.name}</div>
                      <div style={{ fontSize: u(1.1), opacity: 0.45, display: 'flex', alignItems: 'center', gap: u(0.6) }}>
                        {extras.status && <span style={{ width: u(0.7), height: u(0.7), borderRadius: '50%', background: asleep ? '#666' : '#4cd964' }} />}
                        {t.sub}{extras.status ? ` · ${t.state}` : ''}
                      </div>
                    </div>
                  )
                })}
              </div>
            </>
          )}
        </div>
      </div>

      <Row>
        <Text>Weight</Text>
        {(['regular', 'duotone', 'fill'] as Weight[]).map(w => (
          <Button key={w} variant={w === weight ? 'primary' : 'ghost'} onClick={() => setWeight(w)}>{w}</Button>
        ))}
      </Row>
      <Row>
        <Text>Apple TV icon</Text>
        {['apple-logo', 'television-simple', 'monitor-play'].map(n => (
          <Button key={n} variant={n === atvIcon ? 'primary' : 'ghost'} onClick={() => setAtvIcon(n)}>{n}</Button>
        ))}
      </Row>
      <Row>
        <Text>PC icon</Text>
        {['game-controller', 'desktop-tower'].map(n => (
          <Button key={n} variant={n === pcIcon ? 'primary' : 'ghost'} onClick={() => setPcIcon(n)}>{n}</Button>
        ))}
        <Spacer />
        <Button variant="ghost" onClick={() => setNight(!night)}>Preview {night ? 'day' : 'night'}</Button>
        <Button variant="ghost" onClick={() => setIdlePreview(!idlePreview)}>{idlePreview ? 'Exit idle' : 'Preview idle'}</Button>
      </Row>

      <Text weight="semibold">What else I'd add (uncheck to skip)</Text>
      {EXTRAS.map(x => (
        <Checkbox key={x.key} label={`${x.label}: ${x.blurb}`} checked={extras[x.key]}
          onChange={(v: any) => setExtras({ ...extras, [x.key]: typeof v === 'boolean' ? v : !!v?.target?.checked })} />
      ))}

      <TextArea name="notes" label="Notes" value={notes} onChange={(e: any) => setNotes(e?.target?.value ?? e)} />
      <Row>
        <Button variant="primary" onClick={() => submit({ weight, atvIcon, pcIcon, extras, notes })}>Build it</Button>
      </Row>
    </Stack>
  )
}

function Spacer() {
  return <div style={{ flex: 1 }} />
}
