package org.mackler.metronome

class AboutFragment extends DialogFragment {
  import AboutFragment._

  override def onCreateDialog(savedInstanceState: Bundle): Dialog = {
    (new AlertDialogBuilder(getActivity())).
    setTitle(R.string.app_name).
    setMessage(content).
    setNeutralButton(R.string.feedback, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {
	val intent = new Intent(ACTION_SEND)
	intent.putExtra(EXTRA_EMAIL, Array("AdamMackler@gmail.com"))
	intent.putExtra(EXTRA_SUBJECT, "Feedback about Safe Metronome")
        intent.setType("message/rfc822")
	startActivity(android.content.Intent.createChooser(intent,"Sending Feedback by Email"))
      }
    }).
    setPositiveButton(R.string.ok, new DialogOnClickListener() {
      def onClick(dialog: DialogInterface, which: Int) {}
    }).
    create()
  }
}

object AboutFragment {
  val content = """|By Adam Mackler
                   |AdamMackler@gmail.com""".stripMargin
}
