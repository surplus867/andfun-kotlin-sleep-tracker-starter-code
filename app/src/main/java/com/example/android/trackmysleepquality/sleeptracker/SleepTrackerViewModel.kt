/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.provider.SyncStateContract.Helpers.insert
import android.provider.SyncStateContract.Helpers.update
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel (
    val database: SleepDatabaseDao,
    application: Application) : AndroidViewModel(application) {

   // Define a variable, tonight, to hold the current night, and make it MutableLiveData.
    private var tonight = MutableLiveData<SleepNight?>()
    // Define a variable, nights. Then getAllNights() from the database and assign to the nights
    val nights = database.getALLNights()

    val nightsString = Transformations.map(nights) { nights ->
        formatNights(nights, application.resources)
    }

    val startButtonVisible = Transformations.map(tonight) {
        // Tonight is null at the beginning. if there is the case. We want the Start Button visible
        null == it
    }

    val stopButtonVisible = Transformations.map(tonight) {
        // If tonight has a value, the Stop should be visible
        null != it
    }

    val clearButtonVisible = Transformations.map(nights) {
      // The clear button should only be visible if there are nights to clear
        it?.isNotEmpty()
    }

    private var _showSnackBarEvent = MutableLiveData<Boolean>()

    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackBarEvent

    fun doneShowingSnackBar() {
        _showSnackBarEvent.value = false
    }

    private val _navigateToSleepQuality = MutableLiveData<SleepNight>()

    // Navigation to sleepQualityFragment.
    val navigateToSleepQuality: LiveData<SleepNight>
    get() = _navigateToSleepQuality

    // Add a doneNavigating() function the resets the event.
    fun doneNavigating() {
        _navigateToSleepQuality.value = null
    }

    // To initialize the tonight variable, create an init block and call initializeTonight()
    init {
        initializeTonight()
    }
    // Implement initializeTonight(). Use the viewModelScope.launch{} to start a coroutine in the ViewModelScope.
    private fun initializeTonight() {
        viewModelScope.launch {
            tonight.value = getTonightFromDatabase()
        }
    }
    // Marked it as suspend because we want to call from the Coroutine inside the init block,
    // and returns a nullable sleepNight, if there is no current started sleepNight.
    private suspend fun getTonightFromDatabase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            // if the startTime does not equal to endTime, there is no night, and return to night
            if(night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }

    }

    fun onStartTracking() {
        viewModelScope.launch {
            val newNight = SleepNight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()
        }
    }

    private suspend fun insert(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.insert(night)
        }
    }

    fun onStopTracking() {
        viewModelScope.launch {
            val oldNight = tonight.value ?: return@launch

            // Update the night in the database to add the end time
            oldNight.endTimeMilli = System.currentTimeMillis()

            update(oldNight)
            // trigger this navigation, when the user taps the STOP Button, you navigate to
            // the SleepQualityFragment to collect quality rating.
            _navigateToSleepQuality.value = oldNight
        }
    }

    private suspend fun update(night: SleepNight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }

    fun onClear() {
        viewModelScope.launch {
           // Clear the database table
            clear()
            //Add clear tonight since it's no longer in the database
            tonight.value = null
            // Show a snackbar message, because it is friendly.
            _showSnackBarEvent.value = true

        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            database.clear()
        }
    }

}
