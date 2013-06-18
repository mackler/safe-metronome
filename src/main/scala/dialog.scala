package org.mackler.metronome

object SoundFragment extends DialogFragment {
  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    val builder = new AlertDialogBuilder(getActivity())
    builder.setTitle(R.string.choose_sound).
    setItems(R.array.sound_names, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	mainActor ! SetSound(which)
      }
    }).
    create()
  }
}

