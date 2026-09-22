/**
 * Web Audio Pure Algorithmic Trophy Sound Synthesizer
 * Implements the TrophySoundManager specification with zero external audio assets.
 * 
 * Arpeggio schedule:
 * 0.00s: G4 (392.00Hz)
 * 0.13s: C5 (523.25Hz)
 * 0.26s: E5 (659.25Hz)
 * 0.40s: G5 (783.99Hz)
 * 0.55s: C6 (1046.50Hz)
 * 0.72s: E6 (1318.51Hz)
 * 
 * Physical damping envelope:
 * 24ms Raised-Cosine Attack (eliminates clicks)
 * Harmonic structure: 72% fundamental + 20% 2nd harmonic + 8% 3rd harmonic
 * Exponential ring decay over 2.2s
 */

interface ArpeggioNote {
  freq: number;
  delay: number;
  weight: number;
}

const ARPEGGIO_NOTES: ArpeggioNote[] = [
  { freq: 392.00, delay: 0.00, weight: 0.90 }, // G4
  { freq: 523.25, delay: 0.13, weight: 0.85 }, // C5
  { freq: 659.25, delay: 0.26, weight: 0.85 }, // E5
  { freq: 783.99, delay: 0.40, weight: 0.90 }, // G5
  { freq: 1046.50, delay: 0.55, weight: 0.95 }, // C6
  { freq: 1318.51, delay: 0.72, weight: 1.00 }, // E6
];

let audioCtx: AudioContext | null = null;

function getAudioContext(): AudioContext {
  if (!audioCtx) {
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    audioCtx = new AudioContextClass();
  }
  if (audioCtx.state === 'suspended') {
    audioCtx.resume();
  }
  return audioCtx;
}

function createRaisedCosineCurve(length: number = 32): Float32Array {
  const curve = new Float32Array(length);
  for (let i = 0; i < length; i++) {
    const t = i / (length - 1);
    // Raised-cosine: 0.5 * (1 - cos(pi * t))
    curve[i] = 0.5 * (1 - Math.cos(Math.PI * t));
  }
  return curve;
}

export function playTrophyUnlockSound() {
  try {
    const ctx = getAudioContext();
    const now = ctx.currentTime;
    const masterGain = ctx.createGain();
    masterGain.gain.setValueAtTime(0.35, now);
    masterGain.connect(ctx.destination);

    const attackCurve = createRaisedCosineCurve(32);
    const attackDuration = 0.024; // 24ms
    const totalDecay = 2.2;       // 2.2s

    for (const note of ARPEGGIO_NOTES) {
      const noteStart = now + note.delay;

      // Note gain node for envelope
      const noteGain = ctx.createGain();
      noteGain.gain.setValueAtTime(0.0001, noteStart);
      
      // Apply 24ms raised-cosine attack curve
      noteGain.gain.setValueCurveAtTime(attackCurve, noteStart, attackDuration);
      
      // Exponential decay to silence over 2.2s
      noteGain.gain.setValueAtTime(note.weight, noteStart + attackDuration);
      noteGain.gain.exponentialRampToValueAtTime(0.00005, noteStart + totalDecay);
      
      noteGain.connect(masterGain);

      // Composite harmonics: 72% fundamental, 20% 2nd harmonic, 8% 3rd harmonic
      const harmonics = [
        { mult: 1, gain: 0.72 },
        { mult: 2, gain: 0.20 },
        { mult: 3, gain: 0.08 },
      ];

      for (const h of harmonics) {
        const osc = ctx.createOscillator();
        const hGain = ctx.createGain();

        osc.type = 'sine';
        osc.frequency.setValueAtTime(note.freq * h.mult, noteStart);

        // Higher harmonics damp down faster
        hGain.gain.setValueAtTime(h.gain, noteStart);
        hGain.gain.exponentialRampToValueAtTime(0.0001, noteStart + (totalDecay / h.mult));

        osc.connect(hGain);
        hGain.connect(noteGain);

        osc.start(noteStart);
        osc.stop(noteStart + totalDecay + 0.1);
      }
    }

    // Gentle tactile haptic tick if supported on the platform
    if ('vibrate' in navigator) {
      try {
        navigator.vibrate(15);
      } catch {}
    }
  } catch (err) {
    console.warn('Audio synthesis failed:', err);
  }
}
