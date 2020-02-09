package com.github.meng.sample;

import android.app.*;
import android.os.*;
import com.github.meng.editor.processor.*;
import com.github.meng.editor.widget.*;
import android.view.View.*;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		setContentView(R.layout.activity_main);
		CodeEditor ce=(CodeEditor) findViewById(R.id.editor);
	
		ce.setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View p1) {
					Toast.makeText(MainActivity.this,"touchhhhh",Toast.LENGTH_SHORT).show();
				}
			});
		ce.setOnTouchListener(new OnTouchListener(){

				@Override
				public boolean onTouch(View p1, MotionEvent p2) {
					Toast.makeText(MainActivity.this,"touchhhhh",Toast.LENGTH_SHORT).show();
					return false;
				}
			});
		CodeEditor.setCodeView(ce,"<html>\n</html>","html",false,true);
	
		ce.setOnTextChange(new CodeEditor.ICodeEditorTextChange(){

				@Override
				public void onTextChange(String str) {
					Toast.makeText(MainActivity.this, str,Toast.LENGTH_LONG).show();
					
				}
			});
		//Toast.makeText(MainActivity.this,"touchhhhh",Toast.LENGTH_SHORT).show();
		
		/*ce.setReadOnly(false);
		ce.setShowExtendedKeyboard(true);
		ce.setLanguage(new HtmlLanguage());*/

		//ce.setClickable(true);
		
   /*  

		 bind:code="@{viewModel.code}"
		 bind:isReadOnly="@{false}"
		 bind:isShowExtendedKeyboard="@{true}"
		 bind:lang="@{viewModel.lang}"
		 bind:layout_constraintBottom_toBottomOf="parent" 


   mViewDataBinding.setVariable(BR.viewModel, codeModel);
        mViewDataBinding.setLifecycleOwner(this);
        mViewDataBinding.editor.setOnTextChange(str -> {
            Toast.makeText(this, mViewDataBinding.editor.getText(),Toast.LENGTH_LONG).show();
        });
*/

    }


}
