package yadav.sourav.medvan;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DriverLoginActivity extends AppCompatActivity {

    private EditText mEmail, mPassword;


    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Button mLogin, mRegistration,mForgetPassword;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_login);

        mAuth = FirebaseAuth.getInstance();
        firebaseAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                if(user!=null && mAuth.getCurrentUser().isEmailVerified()){
                    Intent intent = new Intent(DriverLoginActivity.this, DriverMapActivity.class);
                    Toast.makeText(DriverLoginActivity.this, "Welcome to MedVan", Toast.LENGTH_SHORT).show();
                    startActivity(intent);
                    finish();
                }

            }
        };
        mForgetPassword = findViewById(R.id.forgetPassword);
        mForgetPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverLoginActivity.this , ResetPasswordActivity.class);
                startActivity(intent);
                finish();

            }
        });

        mEmail= findViewById(R.id.email);
        mPassword=  findViewById(R.id.password);

        mLogin= findViewById(R.id.login);
        mRegistration= findViewById(R.id.registration);


        mRegistration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverLoginActivity.this, DriverSignupActivity.class);
                startActivity(intent);
                return;
            }
        });


        mLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = mEmail.getText().toString().trim();
                final String password = mPassword.getText().toString().trim();

                // Validate input fields
                if (email.isEmpty()) {
                    mEmail.setError("Email is required");
                    mEmail.requestFocus();
                    return;
                }

                if (password.isEmpty()) {
                    mPassword.setError("Password is required");
                    mPassword.requestFocus();
                    return;
                }

                // Show loading indicator
                Toast.makeText(DriverLoginActivity.this, "Signing in...", Toast.LENGTH_SHORT).show();

                mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(DriverLoginActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            String errorMessage = "Sign in failed";
                            if (task.getException() != null) {
                                String error = task.getException().getMessage();
                                if (error.contains("no user record")) {
                                    errorMessage = "No account found with this email";
                                } else if (error.contains("password is invalid")) {
                                    errorMessage = "Incorrect password";
                                } else if (error.contains("network error")) {
                                    errorMessage = "Network error. Please check your connection";
                                }
                            }
                            Toast.makeText(DriverLoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        } else {
                            if (mAuth.getCurrentUser() != null) {
                                if (mAuth.getCurrentUser().isEmailVerified()) {
                                    String user_id = mAuth.getCurrentUser().getUid();
                                    DatabaseReference current_user_db = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(user_id);
                                    current_user_db.setValue(true);
                                } else {
                                    Toast.makeText(DriverLoginActivity.this, "Please verify your email first", Toast.LENGTH_LONG).show();
                                    mAuth.signOut();
                                }
                            }
                        }
                    }
                });
            }
        });
    }
    @Override
    protected void onStart(){
        super.onStart();
        mAuth.addAuthStateListener(firebaseAuthListener);
    }
    @Override
    protected void onStop(){
        super.onStop();
        mAuth.removeAuthStateListener(firebaseAuthListener);
    }}
