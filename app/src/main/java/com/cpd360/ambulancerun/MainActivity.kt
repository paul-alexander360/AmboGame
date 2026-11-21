package com.cpd360.ambulancerun

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        setContent { HomeScreen(version) }
    }
}

@Composable
fun HomeScreen(version: String) {
    val rs = remember { RouteState() }
    val fast = remember { City.fastest() }
    val par = fast * 1.6
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF9BD468))
            .safeDrawingPadding()
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFF6A3D), RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Call: 214 Elm St", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("Par " + City.fmt(par), color = Color(0xFFBF360C), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        CityMap(rs.path, rs.done, { rs.move(it) }, Modifier.fillMaxWidth().aspectRatio(358f / 280f))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!rs.done) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Drag from the station to the glowing building.", fontSize = 15.sp)
                }
            } else {
                val user = City.pathTime(rs.path.toList())
                val rating = City.rating(user, fast)
                val failed = user > par
                val names = listOf("Green", "Yellow", "Orange", "Red")
                val cols = listOf(Color(0xFF4DC43B), Color(0xFFFFD54F), Color(0xFFFF9800), Color(0xFFE53935))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (failed) "Too slow! Call failed" else "Route complete", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Your time " + City.fmt(user) + "   Fastest " + City.fmt(fast), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(cols[rating], RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(names[rating] + " route", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .border(2.dp, Color(0xFF7C80C8), RoundedCornerShape(16.dp))
                            .clickable { rs.reset() }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text("Test again", fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Version $version", color = Color.White, fontSize = 12.sp)
        }
    }
}
