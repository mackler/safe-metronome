package org.mackler.metronome

class StartingTempoDialog extends DialogFragment {
  var mMinutes: Int = 0
  var mStartTempo: Int = 0
  var mMaxTempo: Int = 0

  def minutes = mMinutes

  override def onCreate (savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val args = if (savedInstanceState != null) savedInstanceState else getArguments
    mMinutes = args.getInt("minutes")
    mStartTempo = args.getInt("startTempo")
    mMaxTempo = args.getInt("maxTempo")
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt("minutes",mMinutes)
    outState.putInt(
      "startTempo",
      getView.findViewById(R.id.tempo_picker).asInstanceOf[NumberPicker].getValue
    )
    outState.putInt("maxTempo", mMaxTempo)
  }
  override def onCreateView (
    inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle
  ): View = {
    this.getDialog.setTitle(getString(R.string.set_start_tempo))
    val tempoLayout: View = inflater.inflate(R.layout.start_tempo, container, false)
    val tempoPicker = tempoLayout.findViewById(R.id.tempo_picker).asInstanceOf[NumberPicker]
    tempoPicker.setMinValue(32)
    tempoPicker.setMaxValue(MainActor.MAX_TEMPO)
    tempoPicker.setValue(mStartTempo)
    tempoPicker.setWrapSelectorWheel(false)

    val tapButton = tempoLayout.findViewById(R.id.dialog_tap_button).asInstanceOf[View]
    tapButton.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
	getActivity.asInstanceOf[MainActivity].onTap(v)
      }
    })
    if (getActivity.asInstanceOf[MainActivity].tapTime != 0) {
      tapButton.setBackgroundResource(android.R.color.holo_blue_bright)
      // cause button to turn back to oronge if not tapped in time
    }

    val startButton = tempoLayout.findViewById(R.id.start_button).asInstanceOf[Button]
    startButton.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
	getActivity.asInstanceOf[MainActivity].startChopsBuilder(tempoPicker.getValue, mMinutes)
      }
    })

    tempoLayout
  }

}

object StartingTempoDialog { def newInstance(minutes: Int, startTempo: Int, maxTempo: Int) = {
  val f = new StartingTempoDialog
  val args = new Bundle
  args.putInt("minutes", minutes)
  args.putInt("startTempo", startTempo)
  args.putInt("maxTempo", maxTempo)
  f.setArguments(args)
  f
}}
