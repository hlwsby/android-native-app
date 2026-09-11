package com.example.nativeapp.ui.posts

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.nativeapp.R
import com.example.nativeapp.data.RetrofitClient
import kotlinx.coroutines.launch

class PostsFragment : Fragment(R.layout.fragment_posts) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_posts)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val errorText = view.findViewById<TextView>(R.id.text_error)

        val adapter = PostsAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val posts = RetrofitClient.api.getPosts()
                adapter.submitList(posts)
            } catch (e: Exception) {
                errorText.visibility = View.VISIBLE
            } finally {
                progress.visibility = View.GONE
            }
        }
    }
}
