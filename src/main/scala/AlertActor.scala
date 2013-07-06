package org.mackler.metronome

class AlertActor extends Actor {
  import AlertActor._

  var trackOption: Option[AudioTrack]   = None
  var dataOption:  Option[Array[Short]] = None
  def track = trackOption.get
  def data  = dataOption.get

  def receive = {
    case Load(context) ⇒
      logD(s"alert actor received Load message")

      val soundData = new Array[Short](boxingShorts)
      readRawSound(context.getResources, R.raw.boxingbell, soundData, boxingShorts)
      dataOption = Option(soundData)
      val bufferSizeInBytes = audioTrackGetMinBufferSize(44100,CHANNEL_OUT_MONO,ENCODING_PCM_16BIT)
      trackOption = Option( new AudioTrack(3,
					   44100,
					   CHANNEL_OUT_MONO,
					   ENCODING_PCM_16BIT,
					   bufferSizeInBytes,
					   android.media.AudioTrack.MODE_STREAM) )

    case Sound ⇒
      track.play()
      track.write(data, 0, data.size)
      track.setNotificationMarkerPosition(boxingShorts)
      track.setPlaybackPositionUpdateListener(resetListener)
  }

  object resetListener extends OnPlaybackPositionUpdateListener {
    def onMarkerReached(track: AudioTrack) {
      track.stop()
      track.release()
      trackOption = None
      dataOption = None
      logD("sounding of alert complete")
    }
    def onPeriodicNotification(track: AudioTrack) {}
  }

}

object AlertActor {
  // A Short is 16 bits, thus this number is half the file size in bytes:
  final val boxingShorts = 111412

  case class Load(context: Activity)
  case object Sound
}
