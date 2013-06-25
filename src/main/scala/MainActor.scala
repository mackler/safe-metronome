package org.mackler.metronome

import scala.concurrent.duration._

class MainActor extends Actor {
  import MainActor._
  implicit val executionContext = context.system.dispatcher

  var mTempo = 0
  var mIsPlaying: Boolean = false

  // sound is raw PCM with sample rate 44100, depth 16 bits, mono, single beat @ 32 BPM
  // ie, 82688 samples per beat (rounded up from 82687.5), file size in 8-bit bytes
  final val FILE_SIZE = 165376
  // TODO: find optimal buffer size programmatically
  var bufferSizeInBytes = 0
 
  var uiOption: Option[MainActivity] = None
  var audioTrackOption: Option[AudioTrack] = None
  val claveAudio = new Array[Short](FILE_SIZE/2)
  val cowbellAudio = new Array[Short](FILE_SIZE/2)
  var audioData = claveAudio

  /*
   * These variables are used by the ChopsBuilder™ feature
   */
  var mTargetTempo : Int  = 0
  var mTargetTime  : Long = 0
  var mTimeLeft    : Int  = 0
  var mChopsTicker      : Option[Cancellable] = None
  var mChopsIncrementer : Option[Cancellable] = None
  var mChopsCompleter   : Option[Cancellable] = None

  private def track = audioTrackOption.get
  private def timeLeft = ((mTargetTime - System.currentTimeMillis) max 0).toInt

  override def preStart {
    bufferSizeInBytes = audioTrackGetMinBufferSize(44100,CHANNEL_OUT_MONO,ENCODING_PCM_16BIT)
    audioTrackOption = Option(
      new AudioTrack(3,
		     44100,
		     CHANNEL_OUT_MONO,
		     ENCODING_PCM_16BIT,
		     bufferSizeInBytes,
		     MODE_STREAM)
    )
  }

  override def postStop {
  }

  val endListener = new OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) {
      // TODO: give visual beat indication
    }
    def onPeriodicNotification(track: AudioTrack) {}
  }

  def runOnUi(f: => Unit) {
    uiOption.get.runOnUiThread(new Runnable { def run { f }})
  }

  def receive = {

    case SetUi(activity) ⇒
      uiOption = Option(activity)
      if (mTempo == 0) {
        val resources = uiOption.get.getResources

        readSound(resources, R.raw.clave,   claveAudio)
        readSound(resources, R.raw.cowbell, cowbellAudio)

	val preferences = uiOption.get.getPreferences(MODE_PRIVATE)
	mTempo = preferences.getInt("tempo", 120)
        audioData = preferences.getString("sound","clave") match {
	  case "clave"   ⇒ claveAudio
	  case "cowbell" ⇒ cowbellAudio
	}
	mTargetTempo = preferences.getInt("targetTempo", 0)
	mTimeLeft = preferences.getInt("timeLeft", 0)
      }
      runOnUi {
        uiOption.get.setTempo(mTempo)
        updateSeek(mTempo)
	if (mTimeLeft > 0) uiOption.get.displayChopsBuilderData(mTargetTempo, mTimeLeft)
      }

    case Start ⇒ if (mIsPlaying != true ) {
      mIsPlaying = true
      // not sure why this next line is necessary, but w/o it I hear one
      // buffer's-worth of the sound sample, as if the first call to write only
      // writes until the buffer is full, but the rest of the samples are lost.
      track.write(new Array[Short](bufferSizeInBytes/2), 0, bufferSizeInBytes/2)
      if (mTimeLeft > 0) // ChopsBuilder™ was paused
	startChopsBuilder()
      self ! PlayLoop
    }

    case PlayLoop ⇒
      val samplesPerBeat = 2646000 / mTempo
      if (mIsPlaying) {
	track.write(audioData, 0, samplesPerBeat)
	track.setNotificationMarkerPosition (samplesPerBeat-1)
	track.setPlaybackPositionUpdateListener(endListener)
	if (track.getPlayState != PLAYSTATE_PLAYING) track.play()
	self ! PlayLoop
      } else track.stop()

    case Stop ⇒
      mIsPlaying = false
      mTimeLeft = mChopsCompleter match {
	case Some(_) ⇒
           // ChopsBuilder™ is active so remember time left and pause it
	  cancelChopsBuilder()
	  timeLeft
	case _ ⇒ 0
      }

    case SetTempo(bpm) ⇒ if (bpm != mTempo) {
      mTempo = bpm
      mChopsCompleter match {
	case Some(_) ⇒
          mTimeLeft = timeLeft
	  startChopsBuilder()
	case _ ⇒
      }
    }

    case SetSound(sound: Int) ⇒ sound match {
      case 0 ⇒ audioData = claveAudio
      case 1 ⇒ audioData = cowbellAudio
    }

    case SavePreferences(preferences) ⇒
      val editor = preferences.edit
      editor.putInt("tempo", mTempo)
      editor.putString("sound", audioData match {
	case `claveAudio`   ⇒ "clave"
	case `cowbellAudio` ⇒ "cowbell"
      })
      editor.putInt("targetTempo", mTargetTempo)
      editor.putInt("timeLeft", timeLeft)
      editor.apply() // is asynchronous

    /** The ChopsBuilder™ feature */

    case BuildChops(startTempo: Int, timeInMinutes: Int) ⇒
      mTargetTempo = mTempo
      mTempo = startTempo
      mTimeLeft = timeInMinutes * 60000
      startChopsBuilder()
      self ! Start

    case ChopsCancel ⇒
      logD(s"ChopsBuilder™ cancelled by user")
      mTimeLeft = 0
      cancelChopsBuilder()

  } // end of receive method

  private def startChopsBuilder() {
    cancelChopsBuilder()
    if (mTempo < mTargetTempo) {
      mChopsTicker = Option(
	context.system.scheduler.schedule(1.seconds,1.seconds)(chopsTick)
      )

      mTargetTime = System.currentTimeMillis + mTimeLeft
      val millisPerIncrement = mTimeLeft / (mTargetTempo - mTempo)

      mChopsIncrementer = Option(context.system.scheduler.schedule(
	millisPerIncrement.milliseconds,
	millisPerIncrement.milliseconds
      )(chopsIncrement))
      mChopsCompleter = Option(
	context.system.scheduler.scheduleOnce(mTimeLeft.milliseconds)(chopsComplete)
      )
    } else {
      mTimeLeft = 0 // if the starting tempo is not less than the target tempo, do nothing
      runOnUi {	uiOption.get.clearBuilder() }
    }
  }

  /* Called both when user cancels, and when tempo is adjusted */
  private def cancelChopsBuilder() {
    if (mChopsTicker.isDefined) mChopsTicker.get.cancel()
    mChopsTicker = None
    if (mChopsIncrementer.isDefined) mChopsIncrementer.get.cancel()
    mChopsIncrementer = None
    if (mChopsCompleter.isDefined) mChopsCompleter.get.cancel()
    mChopsCompleter = None
  }

  private def chopsIncrement {
    mTempo += 1
    updateSeek(mTempo)
  }

  private def chopsTick {
    runOnUi { uiOption.get.updateCountdown(((mTargetTime - System.currentTimeMillis) / 1000).toInt) }
  }

  private def chopsComplete {
    logD(s"ChopsBuilder™ complete")
    cancelChopsBuilder()
    runOnUi { uiOption.get.clearBuilder() }
  }

  private def updateSeek(bpm: Int) {
    runOnUi {
      uiOption.get.setTempo(mTempo)
      uiOption.get.setSeek(mTempo-32)
    }
  }

  private def playStateString(state: Int): String = {
    state match {
      case PLAYSTATE_STOPPED => "stopped"
      case PLAYSTATE_PAUSED  => "paused"
      case PLAYSTATE_PLAYING => "playing"
      case _ => "unknown"
    }
  }

  private def readSound(resources: Resources, source: Int, dest: Array[Short]) {
    val dataInputStream = new java.io.DataInputStream(resources.openRawResource(source))
    (0 to ((FILE_SIZE/2)-1)) foreach {
      dest.update(_, dataInputStream.readShort())
    }
    dataInputStream.close()
  }
}

object MainActor {
  // Tempo range is same as Korg KDM-2
  final val MIN_TEMPO = 32
  final val MAX_TEMPO = 252
}
