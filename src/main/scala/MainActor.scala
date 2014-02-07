package org.mackler.metronome

import scala.concurrent.duration._

class MainActor extends Actor with RequiresMessageQueue[UnboundedMessageQueueSemantics] {
  import MainActor._
  implicit private val executionContext = context.system.dispatcher

  private var mTempo: Float = 0
  private var mIsPlaying: Boolean = false
  private var mBeatsPerMeasure = 0
  private var mBeat = 0

  // Next three lines refer to ChopsBuilder™
  private var mIsPaused      = false
  private var mMillisecondsLeft: Int = 0
  private var mTargetTempo: Float    = 0

  // sound is raw PCM with sample rate 44100, depth 16 bits, big-endian, mono,
  // single beat @ 32 BPM, ie, 82688 samples per beat (rounded up from 82687.5)
  // file size in 8-bit bytes
  private final val FILE_SIZE = 165376
  private var bufferSizeInBytes = 0
 
  private var uiOption: Option[MainActivity] = None

  private val claveAudio = new Array[Short](FILE_SIZE/2)
  private val claveAccentAudio = new Array[Short](FILE_SIZE/2)
  private val cowbellAudio = new Array[Short](FILE_SIZE/2)
  private val cowbellAccentAudio = new Array[Short](FILE_SIZE/2)
  private var audioData = claveAudio
  private var audioAccentData = claveAccentAudio
  private var audioTrackOption: Option[AudioTrack] = None
  private def track = audioTrackOption.get

  private val alertActor = context.system.actorOf(Props[AlertActor],"alertActor")

  /* When ChopsBuilder™ is running, this ticker causes a runnable to
   * be sent to the UI to update the countdown time display */
  private var mChopsTicker: Option[Cancellable] = None

  override def preStart {
    bufferSizeInBytes = audioTrackGetMinBufferSize(44100,CHANNEL_OUT_MONO,ENCODING_PCM_16BIT)
    audioTrackOption = Option( new AudioTrack(
      3,
      44100,
      CHANNEL_OUT_MONO,
      ENCODING_PCM_16BIT,
      bufferSizeInBytes,
      MODE_STREAM
    ))
  }

  val endListener = new OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) {
      // TODO add visual beat indicator
    }
    def onPeriodicNotification(track: AudioTrack) {}
  }

  // TODO: check to make sure there's actually a user interface
  def runOnUi(f: => Unit) {
    uiOption.get.runOnUiThread(new Runnable { def run { f }})
  }

  private def startTicker() {
    if (mChopsTicker.isDefined) mChopsTicker.get.cancel()
    mChopsTicker = Option (
      context.system.scheduler.schedule(1.seconds, 1.seconds, self, Tick)
    )
  }
  private def stopTicker() {
    if (mChopsTicker.isDefined) mChopsTicker.get.cancel()
    mChopsTicker = None
  }

  def receive = {

    case SetUi(activity) ⇒
      uiOption = Option(activity)
      if (mTempo == 0) {
	/* This is the first time this message has been received since
	 * construction of this Actor, thus read saved preferences. */
        val resources = uiOption.get.getResources

        readSound(resources, R.raw.clave,   claveAudio)
        readSound(resources, R.raw.cowbell, cowbellAudio)
        readSound(resources, R.raw.clave_accent,   claveAccentAudio)
        readSound(resources, R.raw.cowbell_accent, cowbellAccentAudio)

	val preferences = uiOption.get.getPreferences(MODE_PRIVATE)
	mTempo = preferences.getFloat("tempo", 120)
        preferences.getString("sound","clave") match {
	  case "clave" ⇒
	    audioData = claveAudio
	    audioAccentData = claveAccentAudio
	  case "cowbell" ⇒
	    audioData = cowbellAudio
	    audioAccentData = cowbellAccentAudio
	}
	mBeatsPerMeasure = preferences.getInt("beatsPerMeasure", 0)
	mTargetTempo = preferences.getFloat("targetTempo", 0)
	mMillisecondsLeft = preferences.getInt("timeLeft", 0)
	if (mMillisecondsLeft > 0) alertActor ! AlertActor.Load(uiOption.get)
      }
      runOnUi {
        uiOption.get.setTempoDisplay(mTempo)
	uiOption.get.hideStartingProgress()
	if (mMillisecondsLeft > 0)
	  uiOption.get.displayChopsBuilderData(mTargetTempo.round.toInt, mMillisecondsLeft)
      }
 
    case Start(_) ⇒ startMetronome()

    case SingleTap ⇒ if (!mIsPlaying) {
      if (track.getPlayState != PLAYSTATE_PLAYING) track.play()
      track.write(audioData, 0, MIN_SAMPLES)
      track setNotificationMarkerPosition MIN_SAMPLES
      track.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener {
	def onMarkerReached(track: AudioTrack) {
	  logD("End of single loop reached")
	  track.stop()
	  track setNotificationMarkerPosition 0
	}
	def onPeriodicNotification(track: AudioTrack) {}
      })
    }

    case PlayLoop ⇒
      if (mIsPlaying) {
	if (mBeatsPerMeasure > 0 && track.getPlayState != PLAYSTATE_PLAYING) mBeat = 1

	val samplesPerBeat = (SAMPLES_PER_MINUTE / mTempo).round

	if (mMillisecondsLeft > 0 && !mIsPaused) { // ChopsBuilder™ is on
	  val millisecondsPerBeat = (samplesPerBeat / 44.1).round.toInt
          mMillisecondsLeft -= millisecondsPerBeat
	  if (mMillisecondsLeft <= 0) chopsComplete(mTargetTempo)
	  else {
	    val portion: Double = millisecondsPerBeat.toDouble / mMillisecondsLeft.toDouble
	    val tempoRange = mTargetTempo - mTempo
	    mTempo += (portion * tempoRange.toDouble).toFloat
	    runOnUi { uiOption.get.setTempoDisplay(mTempo) }
	  }
	}
	if (track.getPlayState != PLAYSTATE_PLAYING) track.play()
	track.write(if (mBeat == 1) audioAccentData else audioData, 0, samplesPerBeat)
	if (mBeatsPerMeasure > 0) {
	  mBeat += 1
	  if (mBeat > mBeatsPerMeasure) mBeat = 1
	}
	self ! PlayLoop
      } else {
	track.stop() // if user stops playing, let loop finish
	stopTicker()
      }
 
    case Stop(_) ⇒ mIsPlaying = false

    case SetTempo(bpm,timestamp) =>
      logD(s"MainActor received SetTempo($bpm) message")
      if (bpm != mTempo) mTempo = bpm

    case SetSound(sound: Int) ⇒ sound match {
      case 0 ⇒
        audioData = claveAudio
        audioAccentData = claveAccentAudio
      case 1 ⇒
        audioData = cowbellAudio
        audioAccentData = cowbellAccentAudio
    }

    case SetAccent(beats: Int) ⇒ {
      logD(s"main actor setting accent beat to $beats")
      if (mBeatsPerMeasure == 0) mBeat = 1
      mBeatsPerMeasure = beats
      if (mBeatsPerMeasure == 0) mBeat = 0
    }

    case SavePreferences(preferences) ⇒
      val editor = preferences.edit
      editor.putFloat("tempo", mTempo)
      editor.putString("sound", audioData match {
	case `claveAudio`   ⇒ "clave"
	case `cowbellAudio` ⇒ "cowbell"
      })
      editor.putInt("beatsPerMeasure", mBeatsPerMeasure)
      if (mMillisecondsLeft > 0) {
        editor.putFloat("targetTempo", mTargetTempo)
        editor.putInt("timeLeft", mMillisecondsLeft)
      } else {
        editor.putFloat("targetTempo", 0)
        editor.putInt("timeLeft", 0)
      }
      editor.apply() // is asynchronous

    /** The ChopsBuilder™ feature */

    case Tick ⇒ chopsTick()

    case BuildChops(startTempo: Float, timeInMinutes: Int) ⇒
      mTargetTempo = mTempo
      mMillisecondsLeft = (timeInMinutes * 60000)
      mTempo = startTempo
      startMetronome()
      alertActor ! AlertActor.Load(uiOption.get)

    case IncrementCountdown ⇒ mMillisecondsLeft += 60000

    case DecrementCountdown ⇒
      mMillisecondsLeft -= 60000
      if (mMillisecondsLeft <= 0) chopsComplete(mTargetTempo)

    case PauseChopsBuilder ⇒
      mIsPaused = true
      stopTicker()

    case ChopsCancel ⇒
      mMillisecondsLeft = 0
      stopTicker()
      alertActor ! UnloadAlert

  } // end of receive method

  private def startMetronome() {
    mIsPaused = false
    if (mMillisecondsLeft > 0) startTicker()
    if (mIsPlaying != true ) {
      mIsPlaying = true
      self ! PlayLoop
    }
  }

  /** Called every second (allegedly) while ChopsBuilder™ is running */
  private def chopsTick() {
    runOnUi { uiOption.get.updateCountdown(mMillisecondsLeft) }
  }

  def chopsComplete(tempo: Float) {
    stopTicker()
    mTempo = tempo
    runOnUi {
      uiOption.get.setTempoDisplay(mTempo)
      uiOption.get.clearBuilder()
    }
    /* ring alert bell */
    alertActor ! AlertActor.Sound
  }

  private def readSound(resources: Resources, source: Int, dest: Array[Short]) {
    readRawSound( resources, source, dest, (FILE_SIZE/2) )
  }
}

object MainActor {
  // Tempo range is same as Korg KDM-2
  final val MIN_TEMPO = 32
  final val MAX_TEMPO = 252
  final val SAMPLES_PER_MINUTE = 44100 * 60
  final val MIN_SAMPLES = (SAMPLES_PER_MINUTE / MAX_TEMPO).round
}

class PriorityMailbox(settings: akka.actor.ActorSystem.Settings, config: com.typesafe.config.Config)
extends akka.dispatch.UnboundedPriorityMailbox (MyComparator)

import akka.dispatch.Envelope
object MyComparator extends java.util.Comparator[Envelope] {
  def compare(e1: Envelope, e2: Envelope): Int = {

    // SetTempo messages must be processed in temporal order:
    e1.message match {
      case SetTempo(bpm,timestamp1) ⇒ e2.message match {
	case SetTempo(bpm,timestamp2) ⇒ return (timestamp1 - timestamp2).toInt
	case _ ⇒
      }
      case _ ⇒
    }

    e1.message match {
      case Start(timestamp1) ⇒ e2.message match {
	case Stop(timestamp2) ⇒ return (timestamp1 - timestamp2).toInt
	case _ ⇒
      }
      case Stop(timestamp1) ⇒ e2.message match {
	case Start(timestamp2) ⇒ return (timestamp1 - timestamp2).toInt
	case _ ⇒
      }
      case _ ⇒
    }

    def priorityVal(message: Any) = message match {
      case _:Stop               ⇒ -6
      case _:Start              ⇒ -6
      case   SingleTap          ⇒ -5
      case _:SavePreferences    ⇒ -4
      case _:SetUi              ⇒ -3
      case _:BuildChops         ⇒ -2
      case _:SetTempo           ⇒ -1
      case   ChopsCancel        ⇒  0
      case   IncrementCountdown ⇒  1
      case   DecrementCountdown ⇒  1
      case   Tick               ⇒  2
      case _:SetSound           ⇒  3
      case _:SetAccent          ⇒  3
      case   PauseChopsBuilder  ⇒  5
      case   PlayLoop           ⇒  10
    }

    val result = priorityVal(e1.message) - priorityVal(e2.message)
    result
  }
}
