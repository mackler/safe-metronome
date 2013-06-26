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
  var mChopsBuilder: Option[ChopsBuilder] = None

  private def track = audioTrackOption.get

  /* When ChopsBuilder™ is running, this ticker causes a runnable to
   * be sent to the UI to update the countdown time display */
  private var mChopsTicker: Option[Cancellable] = None

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
    mChopsBuilder = Option(new ChopsBuilder(context.system.scheduler))
  }

  private def chopsBuilder = mChopsBuilder.get

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
	/* This is the first time this message has been received since
	 * construction of this Actor, thus read saved preferences. */
        val resources = uiOption.get.getResources

        readSound(resources, R.raw.clave,   claveAudio)
        readSound(resources, R.raw.cowbell, cowbellAudio)

	val preferences = uiOption.get.getPreferences(MODE_PRIVATE)
	mTempo = preferences.getInt("tempo", 120)
        audioData = preferences.getString("sound","clave") match {
	  case "clave"   ⇒ claveAudio
	  case "cowbell" ⇒ cowbellAudio
	}
	chopsBuilder.setTargetTempo(preferences.getInt("targetTempo", 0))
	chopsBuilder.setMilliseconds(preferences.getInt("timeLeft", 0))
      }
      runOnUi {
        uiOption.get.setTempoDisplay(mTempo)
	if (chopsBuilder.isOn)
	  uiOption.get.displayChopsBuilderData(chopsBuilder.targetTempo, chopsBuilder.formattedTime)
      }

    case Start ⇒ if (mIsPlaying != true ) {
      mIsPlaying = true
      /* not sure why this next line is necessary, but w/o it I hear one
       * buffer's-worth of the sound sample, as if the first call to write only
       * writes until the buffer is full, but the rest of the samples in the
       * loop are lost. */
      track.write(new Array[Short](bufferSizeInBytes/2), 0, bufferSizeInBytes/2)
      chopsBuilder.synchronized { if (chopsBuilder.isPaused) startChopsBuilder() }
      self ! PlayLoop
    }

    case PlayLoop ⇒
      if (mIsPlaying) {
	chopsBuilder.synchronized { if (chopsBuilder.isRunning) {
	  val newTempo = chopsBuilder.currentTempo.round
	  if (newTempo != mTempo) {
	    mTempo = newTempo
	    runOnUi {
              uiOption.get.setTempoDisplay(mTempo)
	    }
	  }
	}}
	val samplesPerBeat = (2646000 / mTempo).round
	track.write(audioData, 0, samplesPerBeat)
	track.setNotificationMarkerPosition (samplesPerBeat-1)
	track.setPlaybackPositionUpdateListener(endListener)
	if (track.getPlayState != PLAYSTATE_PLAYING) track.play()
	self ! PlayLoop
      } else track.stop() // if user stops playing, let loop finish

    case Stop ⇒
      mIsPlaying = false
      chopsBuilder.synchronized { if (chopsBuilder.isRunning) chopsBuilder.pause() }

    case SetTempo(bpm) ⇒ if (bpm != mTempo) {
      mTempo = bpm
      chopsBuilder.synchronized {
	if (chopsBuilder.isRunning) {
	  chopsBuilder.adjust(mTempo)
	}
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
      chopsBuilder.synchronized { if (chopsBuilder.isOn) {
        editor.putInt("targetTempo", chopsBuilder.targetTempo)
        editor.putInt("timeLeft", chopsBuilder.milliseconds)
      } else {
        editor.putInt("targetTempo", 0)
        editor.putInt("timeLeft", 0)
      }}
      editor.apply() // is asynchronous

    /** The ChopsBuilder™ feature */

    case BuildChops(startTempo: Int, timeInMinutes: Int) ⇒
      logD(s"main actor received BuildChops, start $startTempo BPM, $timeInMinutes minutes")
      chopsBuilder.synchronized {
        chopsBuilder.setTargetTempo(mTempo)
        chopsBuilder.setMilliseconds(timeInMinutes * 60000)
      }
      mTempo = startTempo
      chopsBuilder.setCompletionFunction(chopsComplete)
      startChopsBuilder
      self ! Start

    case ChopsCancel ⇒
      chopsBuilder.synchronized { if (chopsBuilder.isOn) chopsBuilder.cancel() }

  } // end of receive method

  private def startChopsBuilder() {
    logD(s"actor's startChopsBuilder() called, mTempo is $mTempo")
    chopsBuilder.synchronized {
    chopsBuilder.reset()
    if (chopsBuilder.start(mTempo)) {
      if (mChopsTicker.isDefined) mChopsTicker.get.cancel()
      mChopsTicker = Option (
	context.system.scheduler.schedule(1.seconds,1.seconds)(chopsTick)
      )
      logD(s"ticker is $mChopsTicker")
    } else runOnUi { uiOption.get.clearBuilder() }
  }}

  /** Called every second while ChopsBuilder™ is running */
  private def chopsTick {
    logD("TICK")
    chopsBuilder.synchronized { if (chopsBuilder.isOn) {
    val secondsLeft = chopsBuilder.formattedTime
    runOnUi { uiOption.get.updateCountdown(secondsLeft) }
  }}}

  def chopsComplete() {
    logD(s"Main actor's ChopsBuilder™ completion function caled")
    runOnUi { uiOption.get.clearBuilder() }
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
