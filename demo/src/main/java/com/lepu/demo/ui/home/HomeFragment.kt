package com.lepu.demo.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.lepu.blepro.ble.BleService
import com.lepu.demo.R
import com.lepu.demo.ScanActivity

class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        val textView: TextView = root.findViewById(R.id.text_home)
        val button: Button = root.findViewById(R.id.o2ring)
        homeViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })
        homeViewModel.button.observe(viewLifecycleOwner, Observer {
            button.text = it
        })

        button.setOnClickListener( View.OnClickListener {
            Intent(context, ScanActivity::class.java).also { intent -> context?.startActivity(intent) }
        })

        return root
    }
}