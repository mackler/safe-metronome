package org.mackler.metronome

import scala.concurrent.duration._

class MainActor extends Actor
with akka.dispatch.RequiresMessageQueue[akka.dispatch.UnboundedMessageQueueSemantics]
 {
  import MainActor._
  implicit val executionContext = context.system.dispatcher

  var mTempo: Float = 0
  var mIsPlaying: Boolean = false
  private var mIsPaused = false // refers to ChopsBuilder™
  var mMillisecondsLeft: Int = 0
  var mTargetTempo: Float = 0

  // sound is raw PCM with sample rate 44100, depth 16 bits, big-endian, mono,
  // single beat @ 32 BPM, ie, 82688 samples per beat (rounded up from 82687.5)
  // file size in 8-bit bytes
  final val FILE_SIZE = 165376
  // TODO: find optimal buffer size programmatically
  var bufferSizeInBytes = 0
 
  var uiOption: Option[MainActivity] = None
  var audioTrackOption: Option[AudioTrack] = None
  val claveAudio = new Array[Short](FILE_SIZE/2)
  val cowbellAudio = new Array[Short](FILE_SIZE/2)
  var audioData = claveAudio

  val alertActor = context.system.actorOf(Props[AlertActor],"alertActor")

  private def track = audioTrackOption.get

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
      // TODO: give visual beat indication
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

	val preferences = uiOption.get.getPreferences(MODE_PRIVATE)
	mTempo = preferences.getFloat("tempo", 120)
        audioData = preferences.getString("sound","clave") match {
	  case "clave"   ⇒ claveAudio
	  case "cowbell" ⇒ cowbellAudio
	}
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
 
    case Start ⇒ startMetronome()

    case PlayLoop ⇒
      if (mIsPlaying) {
	val samplesPerBeat = (2646000 / mTempo).round
//	track.setNotificationMarkerPosition (samplesPerBeat-1)
//	track.setPlaybackPositionUpdateListener(endListener)

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
	track.write(audioData, 0, samplesPerBeat)
	self ! PlayLoop
      } else {
	track.stop() // if user stops playing, let loop finish
	stopTicker()
      }
 
    case Stop ⇒ mIsPlaying = false

    case SetTempo(bpm,timestamp) ⇒ if (bpm != mTempo) mTempo = bpm

    case SetSound(sound: Int) ⇒ sound match {
      case 0 ⇒ audioData = claveAudio
      case 1 ⇒ audioData = cowbellAudio
    }

    case SavePreferences(preferences) ⇒
      val editor = preferences.edit
      editor.putFloat("tempo", mTempo)
      editor.putString("sound", audioData match {
	case `claveAudio`   ⇒ "clave"
	case `cowbellAudio` ⇒ "cowbell"
      })
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

  /** Called every second while ChopsBuilder™ is running */
  private def chopsTick() {
    // logD("TICK")
    runOnUi { uiOption.get.updateCountdown(mMillisecondsLeft) }
  }

  def chopsComplete(tempo: Float) {
    logD(s"ChopsBuilder™ complete, reached target of ${tempo.round} BPM")
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

    def priorityVal(message: Any) = message match {
      case   Stop               ⇒ -6
      case _:SavePreferences    ⇒ -5
      case _:SetUi              ⇒ -4
      case _:BuildChops         ⇒ -3
      case _:SetTempo           ⇒ -1
      case   ChopsCancel        ⇒  0
      case   IncrementCountdown ⇒  1
      case   DecrementCountdown ⇒  1
      case   Tick               ⇒  2
      case _:SetSound           ⇒  3
      case   Start              ⇒  4
      case   PauseChopsBuilder  ⇒  5
      case   PlayLoop           ⇒  10
    }

    val result = priorityVal(e1.message) - priorityVal(e2.message)
    result
  }
}
