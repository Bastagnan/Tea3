package com.example.tea

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Request
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

//modèle de la réponse de l'api à la requête d'affichage des listes
data class ResponseModel(
    val version: Double,
    val success: Boolean,
    val status: Int,
    val apiname: String,
    val lists: List<ListItem>
)

data class ListItem(
    val id: String,
    val label: String
)

data class ListOfItems(
    val id: String,
    val label: String,
    val items: MutableList<Item> = mutableListOf()
)

data class Item(
    val id: String,
    val label: String,
    val url: String?,
    val checked: String
)

//l'adapter pour le recyclerview
class MyListAdapter(
    val items: MutableList<ListOfItems>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<MyListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewItem: TextView = itemView.findViewById(R.id.textViewItem)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val listId = items[position].id
                    onItemClick(listId)
                }
            }
        }

        fun bind(item: ListOfItems) {
            textViewItem.text = item.label
        }
    }
}

class ChoixListActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var hash: String
    private lateinit var pseudo: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MyListAdapter
    private lateinit var baseUrl: String

    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choix_list)

        //la toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        //les différents objets à instancier
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        hash = intent.getStringExtra("hash").orEmpty()
        pseudo = intent.getStringExtra("pseudo").orEmpty()
        recyclerView = findViewById(R.id.recyclerViewLists)

        //l'interraction de cliquer sur une liste lance l'activité suivante :
        adapter = MyListAdapter(mutableListOf()) { listId ->
            val intent = Intent(this@ChoixListActivity, ShowListActivity::class.java)
            intent.putExtra("listId", listId)
            intent.putExtra("pseudo", pseudo)
            intent.putExtra("hash", hash)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        //Variables pour creer une nouvelle liste
        val editTextListName: EditText = findViewById(R.id.editTextListName)
        val buttonAddList: Button = findViewById(R.id.buttonAddList)

        //listener en cas de nouvelle liste (clique sur le bouton nouvelle liste)
        buttonAddList.setOnClickListener {
            val listName = editTextListName.text.toString().trim()
            if (listName.isNotEmpty()) {
                // Appeler la fonction pour créer une liste
                createList(listName)
                editTextListName.text.clear()
            }
        }

        // Récupérer la baseUrl depuis les SharedPreferences
        baseUrl = sharedPreferences.getString("baseUrl", "http://tomnab.fr/todo-api/")!!

        // Initialiser Retrofit avec le baseUrl récupéré
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ApiService::class.java)

        // Effectuer la requête GET avec Retrofit
        val call = apiService.getLists(hash)
        logRequest(call.request())
        call.enqueue(object : Callback<ResponseModel> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(call: Call<ResponseModel>, response: Response<ResponseModel>) {
                if (response.isSuccessful) {
                    val responseBody = response.body()
                    if (responseBody != null) {
                        val lists = responseBody.lists

                        // Vérifier si la liste n'est pas nulle avant de procéder
                        for (listItem in lists) {
                            val newList = ListOfItems(listItem.id, listItem.label)
                            adapter.items.add(newList)
                        }
                        adapter.notifyDataSetChanged()
                    } else {
                        Log.e("API Response", "Response body is null")
                    }
                } else {
                    Log.e("API Response", "Request failed with code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ResponseModel>, t: Throwable) {
                Log.e("API Response", "Request failed: ${t.message}")
            }
        })
    }

    private fun logRequest(request: Request) {
        val url = request.url.toString()
        val method = request.method
        val headers = request.headers

        val stringBuilder = StringBuilder()
        stringBuilder.append("API Request\n")
        stringBuilder.append("URL: $url\n")
        stringBuilder.append("Method: $method\n")
        stringBuilder.append("Headers: $headers\n")

        Log.d("API Request", stringBuilder.toString())
    }
    //Fonction de creation d'une nouvelle liste
    private fun createList(listName: String) {
        val call = apiService.createList(hash, listName)
        call.enqueue(object : Callback<ListItem> {
            override fun onResponse(call: Call<ListItem>, response: Response<ListItem>) {
                if (response.isSuccessful) {
                    val createdList = response.body()
                    if (createdList != null) {
                        val newList = ListOfItems(createdList.id, createdList.label)
                        adapter.items.add(newList)
                        adapter.notifyItemInserted(adapter.items.size - 1)
                        recyclerView.scrollToPosition(adapter.items.size - 1)
                    } else {
                        Log.e("API Response", "La liste créée est nulle")
                    }
                } else {
                    Log.e("API Response", "Erreur de code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<ListItem>, t: Throwable) {
                Log.e("API Response", "Echec de la requete: ${t.message}")
            }
        })
    }

    // Interface définissant les méthodes d'API
    interface ApiService {
        @GET("lists")
        fun getLists(@Query("hash") hash: String): Call<ResponseModel>

        @POST("lists")
        fun createList(@Header("hash") hash: String, @Query("label") label: String): Call<ListItem>
    }
}
