package org.mackler.metronome

class CountdownDialog extends DialogFragment {
  var mMinutes: Int = 0

  override def onCreate (savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    mMinutes = if (savedInstanceState != null) savedInstanceState.getInt("minutes")
               else getArguments.getInt("minutes")
  }

  override def onCreateView (
    inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle
  ): View = {
    this.getDialog.setTitle("1/2 " + getString(R.string.set_countdown))

    val v: View = inflater.inflate(R.layout.countdown, container, false)
    val np: NumberPicker = v.findViewById(R.id.minute_count).asInstanceOf[NumberPicker]
    np.setMinValue(1)
    np.setMaxValue(60)
    np.setWrapSelectorWheel(false)
    np.setValue(mMinutes)
    np.setOnValueChangedListener (new OnValueChangeListener {
      def onValueChange(picker: NumberPicker, oldVal: Int, newVal: Int) { mMinutes = newVal }
    })

    val button = v.findViewById(R.id.next_button).asInstanceOf[Button]
    button.setOnClickListener( new android.view.View.OnClickListener {
      def onClick(v: View) {
//	mMinutes = np.getValue
	getActivity.asInstanceOf[MainActivity].showStartingTempoDialog(mMinutes)
      }
    })

    v
  }

  override def onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putInt("minutes", mMinutes)
  }

}

object CountdownDialog { def newInstance(minutes: Int) = {
  val f = new CountdownDialog
  val args = new Bundle
  args.putInt("minutes", minutes)
  f.setArguments(args)
  f
}}
