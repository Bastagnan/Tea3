package com.example.tea

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editTextPseudo: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var buttonOk: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Ce qui concerne la toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        //On instancie les éléments graphiques complétables
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        editTextPseudo = findViewById(R.id.editTextPseudo)
        editTextPassword = findViewById(R.id.editTextPassword)
        buttonOk = findViewById(R.id.buttonOK)

        //On retient le dernier pseudo renseigné
        val lastPseudo = sharedPreferences.getString("pseudo", "")
        editTextPseudo.setText(lastPseudo)

        //Listener du bouton de connexion avec condition sur les champs de connexion non vides
        buttonOk.setOnClickListener {
            val pseudo = editTextPseudo.text.toString().trim()
            val password = editTextPassword.text.toString().trim()

            if (pseudo.isNotEmpty() && password.isNotEmpty()) {
                savePseudo(pseudo)
                authenticateUser(pseudo, password)
            } else {
                displayErrorDialog("Erreur", "Veuillez saisir un pseudo et un mot de passe.")
            }
        }

        // Mettre à jour l'état initial du bouton
        updateButtonStatus()
    }
    override fun onResume() {
        super.onResume()
        // Pré-remplissage du champ pseudo avec la dernière valeur enregistrée
        val lastPseudo = sharedPreferences.getString("pseudo", "")
        editTextPseudo.setText(lastPseudo)

        // Mettre à jour l'état du bouton
        updateButtonStatus()
    }
    //Sauvegarde du pseudo
    private fun savePseudo(pseudo: String) {
        val editor = sharedPreferences.edit()
        editor.putString("pseudo", pseudo)
        editor.apply()
    }
    //Sauvegarde du hash
    private fun saveHash(hash: String) {
        val editor = sharedPreferences.edit()
        editor.putString("hash", hash)
        editor.apply()
    }
    //fonction de connexion à l'api
    private fun authenticateUser(pseudo: String, password: String) {
        //Lien de base pour l'api
        val baseUrl = sharedPreferences.getString("base_url", "")
        //Constructeur de requête
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl.toString())
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClient())
            .build()
        val service = retrofit.create(AuthenticationService::class.java)
        val call = service.authenticate(pseudo, password)
        // Envoi de la requête
        call.enqueue(object : Callback<AuthenticationResponse> {
            override fun onResponse(
                call: Call<AuthenticationResponse>,
                response: Response<AuthenticationResponse>
            ) {
                //Si la réponse est positive, on récupère le hash
                if (response.isSuccessful) {
                    val authenticationResponse = response.body()
                    val hash = authenticationResponse?.hash
                    updateHash(hash!!)
                    startChoixListActivity(pseudo,hash)
                    //Sinon message d'erreur
                } else {
                    displayErrorDialog(
                        "Erreur",
                        "L'identification a échoué"
                    )
                }
            }
            //En cas d'erreur
            override fun onFailure(call: Call<AuthenticationResponse>, t: Throwable) {
                displayErrorDialog(
                    "Erreur",
                    "Une erreur réseau s'est produite."
                )
            }
        })
    }

    private fun updateHash(hash: String) {
        val editor = sharedPreferences.edit()
        editor.putString("hash", hash)
        editor.apply()
    }

    //modèle de la requête
    interface AuthenticationService {
        @FormUrlEncoded
        @POST("authenticate")
        fun authenticate(
            @Field("user") user: String,
            @Field("password") password: String
        ): Call<AuthenticationResponse>
    }

    data class AuthenticationResponse(
        @SerializedName("hash") val hash: String?
    )
    //Fonction de lancement de l'activité suivante avec le pseudo en argument
    private fun startChoixListActivity(pseudo: String, hash: String) {
        val intent = Intent(this, ChoixListActivity::class.java)
        intent.putExtra("pseudo", pseudo)
        intent.putExtra("hash",hash)
        startActivity(intent)
    }

    private fun displayErrorDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }
    //pour le menu actionbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                openSettingsActivity()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    //Pour lancer l'activité des settings
    private fun openSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onStop() {
        super.onStop()
        val pseudo = editTextPseudo.text.toString().trim()
        savePseudo(pseudo)
    }
    //Permet d'avoir un retour dans le logcat des messages d'erreurs liés aux échanges avec l'api
    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                Log.d("Retrofit", message)
            }
        })
        loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }
    //Afin de mettre à jour le bouton de connexion (grisé si l'url de base est vide)
    private fun updateButtonStatus() {
        val baseUrl = sharedPreferences.getString("base_url", "")
        buttonOk.isEnabled = baseUrl?.isNotEmpty() == true
    }
}
