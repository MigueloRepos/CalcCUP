package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CubanBanknotes
import com.example.data.repository.BanknoteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Contador CUP", appName)
    }

    @Test
    fun `verify cuban banknote repository calculations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BanknoteRepository(context)

        // Clear initial
        repo.clearAll()
        assertEquals(0, repo.counts.value.values.sum())

        // Add 5 bills of 1000 CUP and 2 bills of 500 CUP
        repo.updateCount(1000, 5)
        repo.updateCount(500, 2)

        val totalAmount = repo.counts.value.entries.sumOf { it.key.toLong() * it.value }
        val totalBills = repo.counts.value.values.sum()

        assertEquals(7, totalBills)
        assertEquals(6000L, totalAmount) // 5000 + 1000 = 6000 CUP
    }
}
