package org.wordpress.android.ui.posts;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.wordpress.android.R;

public class PostSettingsInputDialogFragment extends DialogFragment {
    interface PostSettingsInputDialogListener {
        void onInputUpdated(String input);
    }

    private static final String INPUT_TAG = "input";
    private String mCurrentInput;
    private PostSettingsInputDialogListener mListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentInput = savedInstanceState.getString(INPUT_TAG, "");
        } else if (getArguments() != null) {
            mCurrentInput = getArguments().getString(INPUT_TAG, "");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INPUT_TAG, mCurrentInput);
    }

    public static PostSettingsInputDialogFragment newInstance(String currentText) {
        PostSettingsInputDialogFragment dialogFragment = new PostSettingsInputDialogFragment();
        Bundle args = new Bundle();
        args.putString(INPUT_TAG, currentText);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.Calypso_AlertDialog);
        LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
        View dialogView = layoutInflater.inflate(R.layout.post_settings_input_dialog, null);
        builder.setView(dialogView);
        final EditText editText = (EditText) dialogView.findViewById(R.id.post_settings_input_dialog_edit_text);
        if (!TextUtils.isEmpty(mCurrentInput)) {
            editText.setText(mCurrentInput);
            // move the cursor to the end
            editText.setSelection(mCurrentInput.length());
        }
        TextView hintTextView = (TextView) dialogView.findViewById(R.id.post_settings_input_dialog_hint);
        hintTextView.setText(R.string.post_excerpt_dialog_hint);

        builder.setTitle(R.string.post_excerpt);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mCurrentInput = editText.getText().toString();
                if (mListener != null) {
                    mListener.onInputUpdated(mCurrentInput);
                }
            }
        });

        return builder.create();
    }

    public void setPostExcerptDialogListener(PostSettingsInputDialogListener listener) {
        mListener = listener;
    }
}
