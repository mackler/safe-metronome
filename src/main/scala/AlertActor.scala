package org.mackler.metronome

class AlertActor extends Actor {
  import AlertActor._

  val boxingbellData = new Array[Short](boxingShorts)
  var trackOption: Option[AudioTrack] = None

  def receive = {
    case Load(context) ⇒
      logD(s"alert actor received Load message")
      readRawSound(context.getResources, R.raw.boxingbell, boxingbellData, boxingShorts)
      val bufferSizeInBytes = audioTrackGetMinBufferSize(44100,CHANNEL_OUT_MONO,ENCODING_PCM_16BIT)
      trackOption = Option(
        new AudioTrack(3,
		     44100,
		     CHANNEL_OUT_MONO,
		     ENCODING_PCM_16BIT,
		     bufferSizeInBytes,
		     android.media.AudioTrack.MODE_STREAM)
      )

    case Sound ⇒
      trackOption.get.play()
      trackOption.get.write(boxingbellData, 0, boxingbellData.size)
      trackOption.get.setNotificationMarkerPosition (boxingShorts)
      trackOption.get.setPlaybackPositionUpdateListener(bellResetListener)
  }

  object bellResetListener extends OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) { track.stop() }
    def onPeriodicNotification(track: AudioTrack) {}
  }

}

object AlertActor {
  // A Short is 16 bits, thus this number is half the file size in bytes:
  final val boxingShorts = 111412

  case class Load(context: Activity)
  case object Sound
}
