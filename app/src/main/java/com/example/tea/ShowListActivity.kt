package com.example.tea

import android.annotation.SuppressLint
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

//forme de la réponse à la requete d'obtention des items d'une liste
data class ItemResponse(
    val version: Double,
    val success: Boolean,
    val status: Int,
    val apiname: String,
    val items: List<Item>
)
//l'adapter du recycle view
class ItemAdapter(val items: MutableList<Item>) : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_item, parent, false)
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

        fun bind(item: Item) {
            textViewItem.text = item.label
        }
    }
}

class ShowListActivity : AppCompatActivity() {
    private lateinit var hash: String
    private lateinit var pseudo: String
    private lateinit var listId: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ItemAdapter
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var retrofit: Retrofit
    private lateinit var apiService: ApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_list)
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        //Les variables utiles pour accéder aux items (pas pseudo)
        listId = intent.getStringExtra("listId").orEmpty()
        pseudo = intent.getStringExtra("pseudo").orEmpty()
        hash = intent.getStringExtra("hash").orEmpty()

        recyclerView = findViewById(R.id.recyclerViewItems)
        adapter = ItemAdapter(mutableListOf())
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        //Pour créer un nouvel item
        val editTextNewItem: EditText = findViewById(R.id.editTextItemName)
        val buttonAddItem: Button = findViewById(R.id.buttonAddItem)
        //Listener du bouton de création
        buttonAddItem.setOnClickListener {
            val newItemLabel = editTextNewItem.text.toString().trim()
            if (newItemLabel.isNotEmpty()) {
                createItem(newItemLabel)
                editTextNewItem.text.clear()
            }
        }

        // Créer Retrofit avec l'URL de base
        retrofit = Retrofit.Builder()
            .baseUrl("http://tomnab.fr/todo-api/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        // Créer l'instance de l'API
        apiService = retrofit.create(ApiService::class.java)

        // Effectuer la requête GET avec Retrofit
        val call = apiService.getItems(hash, listId)
        call.enqueue(object : Callback<ItemResponse> {
            @SuppressLint("NotifyDataSetChanged")
            override fun onResponse(call: Call<ItemResponse>, response: Response<ItemResponse>) {
                if (response.isSuccessful) {
                    val itemResponse = response.body()
                    if (itemResponse != null) {
                        val items = itemResponse.items
                        adapter.items.addAll(items)
                        adapter.notifyDataSetChanged()
                    } else {
                        Log.e("API Response", "Response body is null")
                    }
                } else {
                    Log.e("API Response", "Request failed with code: ${response.code()}")
                }

                // Afficher les détails de la requête
                val request = call.request()
                val requestMethod = request.method
                val requestUrl = request.url
                val requestHeaders = request.headers
                Log.d("API Request", "Method: $requestMethod")
                Log.d("API Request", "URL: $requestUrl")
                Log.d("API Request", "Headers: $requestHeaders")
                Log.d("API Request", "Headers1: $hash")

                // Afficher les détails de la réponse
                val responseCode = response.code()
                val responseHeaders = response.headers()
                Log.d("API Response", "Code: $responseCode")
                Log.d("API Response", "Headers: $responseHeaders")
            }

            override fun onFailure(call: Call<ItemResponse>, t: Throwable) {
                Log.e("API Response", "Request failed: ${t.message}")
            }
        })
    }
    //Fonction de création d'un noucel item
    private fun createItem(label: String) {
        val call = apiService.createItem(hash, listId, label)
        call.enqueue(object : Callback<Item> {
            override fun onResponse(call: Call<Item>, response: Response<Item>) {
                if (response.isSuccessful) {
                    val createdItem = response.body()
                    if (createdItem != null) {
                        adapter.items.add(createdItem)
                        adapter.notifyItemInserted(adapter.items.size - 1)
                        recyclerView.scrollToPosition(adapter.items.size - 1)
                    } else {
                        Log.e("API Response", "Created item is null")
                    }
                } else {
                    Log.e("API Response", "Request failed with code: ${response.code()}")
                }
            }

            override fun onFailure(call: Call<Item>, t: Throwable) {
                Log.e("API Response", "Request failed: ${t.message}")
            }
        })
    }

    // Interface définissant les méthodes d'API
    interface ApiService {
        // ...
        @GET("lists/{listId}/items")
        fun getItems(
            @Header("hash") hash: String,
            @Path("listId") listId: String
        ): Call<ItemResponse>

        @POST("lists/{listId}/items")
        fun createItem(
            @Header("hash") hash: String,
            @Path("listId") listId: String,
            @Query("label") label: String
        ): Call<Item>
    }
}
