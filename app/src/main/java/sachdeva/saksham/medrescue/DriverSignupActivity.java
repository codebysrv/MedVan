package sachdeva.saksham.medrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class DriverSignupActivity extends AppCompatActivity {
    private static final String TAG = "DriverSignupActivity";
    
    private EditText mEmail, mPassword;
    private Button mSignup, mLogin;
    private FirebaseAuth mAuth;
    private DatabaseReference mDriverDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_driver_signup);
            
            // Initialize Firebase Auth
            mAuth = FirebaseAuth.getInstance();
            
            // Initialize Firebase Database
            mDriverDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers");

            // Initialize views
            mEmail = findViewById(R.id.email);
            mPassword = findViewById(R.id.password);
            mSignup = findViewById(R.id.signup);
            mLogin = findViewById(R.id.login);

            // Set up click listeners
            mSignup.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        final String email = mEmail.getText().toString().trim();
                        final String password = mPassword.getText().toString().trim();

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

                        if (password.length() < 6) {
                            mPassword.setError("Password must be at least 6 characters");
                            mPassword.requestFocus();
                            return;
                        }

                        // Show progress
                        mSignup.setEnabled(false);
                        mSignup.setText("Creating Account...");

                        mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(DriverSignupActivity.this, new OnCompleteListener<AuthResult>() {
                                @Override
                                public void onComplete(@NonNull Task<AuthResult> task) {
                                    if (task.isSuccessful()) {
                                        String user_id = mAuth.getCurrentUser().getUid();
                                        DatabaseReference current_user_db = mDriverDatabase.child(user_id);
                                        current_user_db.child("email").setValue(email);
                                        
                                        // Send email verification
                                        mAuth.getCurrentUser().sendEmailVerification()
                                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    if (task.isSuccessful()) {
                                                        Toast.makeText(DriverSignupActivity.this, 
                                                            "Registration successful! Please check your email for verification.", 
                                                            Toast.LENGTH_LONG).show();
                                                        
                                                        // Sign out until email is verified
                                                        mAuth.signOut();
                                                        
                                                        // Go to login screen
                                                        Intent intent = new Intent(DriverSignupActivity.this, DriverLoginActivity.class);
                                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                        startActivity(intent);
                                                        finish();
                                                    } else {
                                                        // Reset button state
                                                        mSignup.setEnabled(true);
                                                        mSignup.setText("Sign Up");
                                                        
                                                        String errorMessage = task.getException() != null ? 
                                                            task.getException().getMessage() : "Failed to send verification email";
                                                        Toast.makeText(DriverSignupActivity.this, 
                                                            "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                                                        Log.e(TAG, "Failed to send verification email", task.getException());
                                                    }
                                                }
                                            });
                                    } else {
                                        // Reset button state
                                        mSignup.setEnabled(true);
                                        mSignup.setText("Sign Up");
                                        
                                        String errorMessage = task.getException() != null ? 
                                            task.getException().getMessage() : "Registration failed";
                                        Toast.makeText(DriverSignupActivity.this, 
                                            "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                                        Log.e(TAG, "Registration failed", task.getException());
                                    }
                                }
                            });
                    } catch (Exception e) {
                        Log.e(TAG, "Error in signup click", e);
                        Toast.makeText(DriverSignupActivity.this, 
                            "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });

            mLogin.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(DriverSignupActivity.this, DriverLoginActivity.class);
                    startActivity(intent);
                    finish();
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is signed in and update UI accordingly
        if (mAuth.getCurrentUser() != null) {
            Intent intent = new Intent(DriverSignupActivity.this, DriverMapActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
} 