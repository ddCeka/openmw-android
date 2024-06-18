/*
    Copyright (C) 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package ui.activity

import com.libopenmw.openmw.R

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import file.GameInstaller
import kotlinx.android.synthetic.main.activity_mods.*
import mods.*
import android.view.MenuItem
import java.io.File

import constants.Constants
import android.view.Menu
import com.codekidlabs.storagechooser.StorageChooser
import com.google.android.material.textfield.TextInputLayout
import android.preference.PreferenceManager
import android.widget.EditText
import android.app.AlertDialog

import android.app.ProgressDialog
import java.io.IOException
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

class ModsActivity : AppCompatActivity() {
    val mPluginAdapter = ModsAdapter()
    val mResourceAdapter = ModsAdapter()
    val mDirAdapter = ModsAdapter()
    val mGroundcoverAdapter = ModsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mods)

        setSupportActionBar(findViewById(R.id.mods_toolbar))

        // Enable the "back" icon in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Switch tabs between plugins/resources
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {

                // Reload mod list when moving from data dir tab
                if(flipper.displayedChild == 2) {
                    updateModList()
                    mPluginAdapter.notifyDataSetChanged()
                    mResourceAdapter.notifyDataSetChanged()
                    mGroundcoverAdapter.notifyDataSetChanged()
                }
		
                flipper.displayedChild = tab.position
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        File(Constants.USER_FILE_STORAGE + "/launcher/ModsDatabases" + 
            PreferenceManager.getDefaultSharedPreferences(this).getString("mods_dir", "")/*.hashCode()*/).mkdirs()

        // Set up adapters for the lists
        setupModList(findViewById(R.id.list_mods), ModType.Plugin)
        setupModList(findViewById(R.id.list_resources), ModType.Resource)
        setupModList(findViewById(R.id.list_dirs), ModType.Dir)
        setupModList(findViewById(R.id.list_groundcovers), ModType.Groundcover)

        updateModList()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateModList()
    }

    private fun updateModList() {
	var dataFilesList = ArrayList<String>()
	dataFilesList.add(GameInstaller.getDataFiles(this))

        val modsDir = PreferenceManager.getDefaultSharedPreferences(this).getString("mods_dir", "")!!
        val database = ModsDatabaseOpenHelper(this.applicationContext)
	// Get list of enabled data directories
	var dataDirs = ArrayList<String>()
	var enabledDataDirs = ArrayList<String>()
	enabledDataDirs.add(GameInstaller.getDataFiles(this))
	dataDirs.add(modsDir)
	val availableDirs = ModsCollection(ModType.Dir, dataDirs, database)

	availableDirs.mods
	    .filter { it.enabled }
            .forEach { enabledDataDirs.add(it.filename) }

	File(modsDir).listFiles().forEach {
	    if (!it.isFile() && enabledDataDirs.contains(it.getName()) )
	        dataFilesList.add(modsDir + it.getName())
	}

        mPluginAdapter.collection = ModsCollection(ModType.Plugin, dataFilesList, database)
        mResourceAdapter.collection = ModsCollection(ModType.Resource, dataFilesList, database)
        mGroundcoverAdapter.collection = ModsCollection(ModType.Groundcover, dataFilesList, database)
    }

    /**
     * Connects a user-interface RecyclerView to underlying mod data on the disk
     * @param list The list displayed to the user
     * @param type Type of the mods this list will contain
     */
    private fun setupModList(list: RecyclerView, type: ModType) {

        // This is here just to auto-enable basic plugins (morrowind.esp...) it somehow dont work in updateModList :( 
	var dataFilesList = ArrayList<String>()

        val modsDir = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("mods_dir", "")!!

	if (type == ModType.Dir) 
            dataFilesList.add(modsDir)
	else {
	    dataFilesList.add(GameInstaller.getDataFiles(this))

	    File(modsDir).listFiles().forEach {
	        if (!it.isFile())
	            dataFilesList.add(modsDir + it.getName())
	    }
        }

        val database = ModsDatabaseOpenHelper(this.applicationContext)
        val linearLayoutManager = LinearLayoutManager(this)
        linearLayoutManager.orientation = RecyclerView.VERTICAL
        list.layoutManager = linearLayoutManager

	if (type == ModType.Plugin) {
	    mPluginAdapter.collection = ModsCollection(type, dataFilesList, database)
            val callback = ModMoveCallback(mPluginAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mPluginAdapter.touchHelper = touchHelper
            list.adapter = mPluginAdapter
	}
	else if (type == ModType.Resource) {
	    mResourceAdapter.collection = ModsCollection(type, dataFilesList, database)
            val callback = ModMoveCallback(mResourceAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mResourceAdapter.touchHelper = touchHelper
            list.adapter = mResourceAdapter
        }
        else if (type == ModType.Dir){
	    mDirAdapter.collection = ModsCollection(type, dataFilesList, database)
            val callback = ModMoveCallback(mDirAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mDirAdapter.touchHelper = touchHelper
            list.adapter = mDirAdapter
        }
	else if (type == ModType.Groundcover){ 
	    mGroundcoverAdapter.collection = ModsCollection(type, dataFilesList, database)
            val callback = ModMoveCallback(mGroundcoverAdapter)
            val touchHelper = ItemTouchHelper(callback)
            touchHelper.attachToRecyclerView(list)
            mGroundcoverAdapter.touchHelper = touchHelper
            list.adapter = mGroundcoverAdapter
        }
    }

    private fun reloadModLists() {
        setupModList(findViewById(R.id.list_mods), ModType.Plugin)
        setupModList(findViewById(R.id.list_resources), ModType.Resource)
        setupModList(findViewById(R.id.list_dirs), ModType.Dir)
        setupModList(findViewById(R.id.list_groundcovers), ModType.Groundcover)

        updateModList()

        mPluginAdapter.notifyDataSetChanged()
        mResourceAdapter.notifyDataSetChanged()
        mDirAdapter.notifyDataSetChanged()
        mGroundcoverAdapter.notifyDataSetChanged()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.clear()
        val inflater = menuInflater
        inflater.inflate(R.menu.mod_manager_settings, menu)

        return super.onPrepareOptionsMenu(menu)
    }


    //duplicate of generateOpenmwCfg() from main activity
    private fun generateDeltaCfg(): String {
        val gamePath = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("game_files", "")!!

        val modsDir = PreferenceManager.getDefaultSharedPreferences(this)
            .getString("mods_dir", "")!!

        val db = ModsDatabaseOpenHelper.getInstance(this)

	var dataFilesList = ArrayList<String>()
	var dataDirsPath = ArrayList<String>()
	dataFilesList.add(GameInstaller.getDataFiles(this))
        dataDirsPath.add(modsDir)

	File(modsDir).listFiles().forEach {
	    if (!it.isFile())
	        dataFilesList.add(modsDir + it.getName())
	}

        val resources = ModsCollection(ModType.Resource, dataFilesList, db)
        val dirs = ModsCollection(ModType.Dir, dataDirsPath, db)
        val plugins = ModsCollection(ModType.Plugin, dataFilesList, db)
        val groundcovers = ModsCollection(ModType.Groundcover, dataFilesList, db)

        // generate final output.cfg
        var output = "data=" + '"' + gamePath + "/Data Files" + '"' + "\n"

        try {
            // output resources
            resources.mods
                .filter { it.enabled }
                .forEach { output += "fallback-archive=${it.filename}\n" }

            // output data dirs
            dirs.mods
                .filter { it.enabled }
                .forEach { output += "data=" + '"' + modsDir + it.filename + '"' + "\n" }

            // output plugins
            plugins.mods
                .filter { it.enabled }
                .forEach { output += "content=${it.filename}\n" }

            // output groundcovers
            groundcovers.mods
                .filter { it.enabled }
                .forEach { output += "groundcover=${it.filename}\n" }

            // write everything to delta.cfg
            output += "data=" + '"' + Constants.USER_FILE_STORAGE + "launcher/delta" + '"' + "\n" 

            File(Constants.USER_FILE_STORAGE + "/launcher/delta").mkdirs()

            output += "\n"

            val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
            lines.removeAll { 
                it.contains("content=delta-merged.omwaddon") ||
                it.contains("groundcover=output_groundcover.omwaddon") ||
                it.contains("content=output_deleted.omwaddon")
            }

            output += lines.joinToString("\n")

            File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg").writeText(output)

        } catch (e: IOException) {
            Log.e("OpenMW-Launcher", "Failed to generate delta.cfg.", e)
        }

        return output
    }

    private fun shellExec(cmd: String? = null, WorkingDir: String? = null): String {
        val output = StringBuilder()

        try {
            val processBuilder = ProcessBuilder()
            if (WorkingDir != null) {
                processBuilder.directory(File(WorkingDir))
            }
            System.setProperty("HOME", "/data/data/$packageName/files/")
            val commandToExecute = arrayOf("/system/bin/sh", "-c", "export HOME=/data/data/$packageName/files/; $cmd")
            processBuilder.command(*commandToExecute)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            process.inputStream.bufferedReader().use { inputStreamReader ->
                var line: String?
                while (inputStreamReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }

            process.waitFor()
        } catch (e: Exception) {

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            output.append("Error executing command: ").append(e.message).append("\nStacktrace:\n").append(sw.toString())

        }

        return output.toString()
    }

    private fun runDeltaCommand(command: String, message: String, logfile: String) {
        val output = StringBuilder()

        // Get the Application Context
        val context = getApplicationContext()

        // Get the nativeLibraryDir (might not be suitable for this case)
        val applicationInfo = context.applicationInfo
        val WorkingDir = applicationInfo.nativeLibraryDir
        val deltaConfigFile = File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg")

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage(message) // Set the message
        progressDialog.setCancelable(false) // Set cancelable to false
        progressDialog.show() // Show the ProgressDialog

        // Execute the command in a separate thread
        Thread {
            val output = shellExec(command, WorkingDir)
            File(logfile).writeText(output)
            runOnUiThread {
                progressDialog.dismiss()
            }
        }.start()
    }

    private fun runGroundcoverify() {
        val grassIds = "grass|kelp|lilypad|fern|thirrlily|spartium|in_cave_plant|reedgroup"
        val excludeIds = "refernce|infernace|planter|_furn_|_skelp|t_glb_var_skeleton|cliffgrass|terr|grassplane|flora_s_m_10_grass|cave_mud_rocks_fern|ab_in_cavemold|rp_mh_rock|ex_cave_grass00|secret_fern"
        val ids_expr = "^(?!.*($excludeIds).*).*($grassIds).*$"
        val exteriorCellRegex = "^[0-9\\-]+x[0-9\\-]+$"

        val command = "./libdelta_plugin.so -v --verbose -c " +
            Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg filter --all --output " +
            Constants.USER_FILE_STORAGE + "/launcher/delta/output_groundcover.omwaddon --desc \"Generated groundcover plugin from your local cavebros\" match Cell --cellref-object-id \"$ids_expr\" --id \"$exteriorCellRegex\" match Static --id \"$ids_expr\" --modify model \"^\" \"grass\\\\\"" +
            " && " +
            "./libdelta_plugin.so -v --verbose -c " + Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg filter --all --output " + Constants.USER_FILE_STORAGE + "/launcher/delta/output_deleted.omwaddon match Cell --cellref-object-id \"$ids_expr\" --id \"$exteriorCellRegex\" --delete" +
            " && " +
            "./libdelta_plugin.so -v --verbose -c " + Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg query --input " + Constants.USER_FILE_STORAGE + "/launcher/delta/output_groundcover.omwaddon --ignore " + Constants.USER_FILE_STORAGE + "/launcher/delta/deleted_groundcover.omwaddon match Static"

        // Get the Application Context
        val context = getApplicationContext()

        // Get the nativeLibraryDir (might not be suitable for this case)
        val applicationInfo = context.applicationInfo
        val WorkingDir = applicationInfo.nativeLibraryDir

        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Running Groundcoverify...") // Set the message
        progressDialog.setCancelable(false) // Set cancelable to false
        progressDialog.show() // Show the ProgressDialog

        Thread {
            val output = shellExec(command.toString(), WorkingDir)
            val outputlines = output.split("\n")
            val modelLines = outputlines.filter { it.trim().startsWith("model:") }
            val paths = modelLines.map { it.substringAfter("model: \"grass").replace("\\\\", "/").trim().replace("\"", "") }
            File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.log").writeText(output)

            File(Constants.USER_FILE_STORAGE + "/launcher/delta/paths.log").writeText(paths.toString())
            paths.forEach { path ->
                val filename = path.substringAfterLast("/")
                val correctedPath = path.substringBeforeLast("/").trim()

                val command2 = "mkdir -p " + Constants.USER_FILE_STORAGE + "/launcher/delta/Meshes/grass/$correctedPath" +
                    " && " +
                    "./libdelta_plugin.so -v --verbose -c " +
                    Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg vfs-extract \"Meshes$correctedPath/$filename\" " +
                    Constants.USER_FILE_STORAGE + "/launcher/delta/Meshes/grass/$correctedPath/$filename"

                shellExec(command2.toString(), WorkingDir)
            }

            runOnUiThread {
                progressDialog.dismiss()
            }

        }.start()
    }

    private fun enableDeltaPlugin() {
        val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
        lines.removeAll { 
            it.contains("delta-merged.omwaddon") 
        }
        lines.add("content=delta-merged.omwaddon")
        File(Constants.USER_CONFIG + "/openmw.cfg").writeText(lines.joinToString("\n"))
    }

    private fun disableDeltaPlugin() {
        val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
        lines.removeAll { 
            it.contains("content=delta-merged.omwaddon") 
        }
        File(Constants.USER_CONFIG + "/openmw.cfg").writeText(lines.joinToString("\n"))
    }

    private fun enableGroundcoverify() {
        val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
        lines.removeAll { 
            it.contains("groundcover=output_groundcover.omwaddon") || it.contains("content=output_deleted.omwaddon") 
        }
        lines.add("content=output_deleted.omwaddon")
        lines.add("groundcover=output_groundcover.omwaddon")
        File(Constants.USER_CONFIG + "/openmw.cfg").writeText(lines.joinToString("\n"))
    }

    private fun disableGroundcoverify() {
        val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
        lines.removeAll { 
            it.contains("groundcover=output_groundcover.omwaddon") || it.contains("content=output_deleted.omwaddon") 
        }
        File(Constants.USER_CONFIG + "/openmw.cfg").writeText(lines.joinToString("\n"))
    }

    /**
     * Makes the "back" icon in the actionbar perform the back operation
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            R.id.action_set_mods_dir -> {

                val chooser = StorageChooser.Builder()
                    .withActivity(this)
                    .withFragmentManager(fragmentManager)
                    .withMemoryBar(true)
                    .allowCustomPath(true)
                    .setType(StorageChooser.DIRECTORY_CHOOSER)
                    .build()

                chooser.show()
                chooser.setOnSelectListener { path -> setupData(path) }
                true
            }

            R.id.action_mods_preset -> {
                var modPresets = arrayOf("Default")
                val currentModsDir = Constants.USER_FILE_STORAGE + "/launcher/ModsDatabases/" + 
                                PreferenceManager.getDefaultSharedPreferences(this).getString("mods_dir", "")!!
                val currentPreset = PreferenceManager.getDefaultSharedPreferences(this).getString("mods_database", "Default")!!
                var currentPresetLocation = 0
                var counter = 1

                File(currentModsDir).listFiles().forEach {  
                    if (it.isFile() && !it.getName().contains("-journal") && it.getName() != "Default") {
                        modPresets += it.getName()
                        if (it.getName() == currentPreset) currentPresetLocation = counter
                        counter += 1
                    }

                }

                AlertDialog.Builder(this)
                .setTitle("Choose content list")
                .setSingleChoiceItems(modPresets, currentPresetLocation) { dialog, which ->
                    updateModList()
                    val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                    with(sharedPref.edit()) {
                        putString("mods_database", modPresets[which])
                       apply()
                    }
                    reloadModLists()
                    dialog.dismiss()
                }
                .setNegativeButton("New") { dialog, which -> 
                    val textInputLayout = TextInputLayout(this)
                    textInputLayout.setPadding(19, 0, 19, 0)
                    val input = EditText(this)
                    textInputLayout.addView(input)

                    val alert = AlertDialog.Builder(this)
                        .setTitle("Create content list")
                        .setView(textInputLayout)
                        .setMessage("Select name.")
                        .setPositiveButton("Create") { dialog, _ ->
                            if (input.text.toString() != "") {
                                updateModList()
                                val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                                with(sharedPref.edit()) {
                                    putString("mods_database", input.text.toString())
                                    apply()
                                }
                                reloadModLists()
                            }
                            dialog.cancel()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.cancel()
                    }.show()
                }
                .setNeutralButton("delete") { dialog, which -> 
                    if (currentPreset != "Default") AlertDialog.Builder(this)
                        .setTitle("Delete current content list")
                        .setMessage("Do you want to delete " + currentPreset + " content list?")
                        .setPositiveButton("Yes") { dialog, _ ->
                            updateModList()
                            val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
                            with(sharedPref.edit()) {
                                putString("mods_database", "Default")
                                apply()
                            }
                            dialog.cancel()
                            reloadModLists() 
                            File(currentModsDir + currentPreset).delete()
                            File(currentModsDir + currentPreset + "-journal").delete()
                        }
                        .setNegativeButton("No") { dialog, _ ->
                            dialog.cancel()
                    }.show()
                }
                .setPositiveButton("Cancel") { dialog, which -> }
                .show()

                true
            }

            R.id.action_tools_delta_merge -> {
                val command = "./libdelta_plugin.so -v --verbose -c " + 
                    Constants.USER_FILE_STORAGE + "/launcher/delta/delta.cfg merge --skip-cells " + 
                    Constants.USER_FILE_STORAGE + "/launcher/delta/delta-merged.omwaddon"

                var isGenerated = false
                var isEnabled = false
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta-merged.omwaddon").exists())
                    isGenerated = true

                val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
                lines.forEach { 
                    if (it.contains("content=delta-merged.omwaddon"))
                        isEnabled = true
                }


                var oldHash = ""
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.hash").exists())
                    oldHash = File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.hash").readText()
                val newHash = generateDeltaCfg().hashCode().toString()

                var message = "Delta plugin is "
                if (!isGenerated) message += "not generated."
                else if (!isEnabled) message += "disabled."
                else message += "enabled."

                if (isGenerated && oldHash != newHash && isEnabled)
                    message += "\n\nAnd may be outdated!"
                else if (isGenerated && isEnabled)
                    message += "\n\nAnd is up to date."
            
                AlertDialog.Builder(this)
                    .setTitle("Delta Merge")
                    .setMessage(message)
                    .setNeutralButton(if (isGenerated) "Re-Generate" else "Generate") { _, _ ->
                        File(Constants.USER_FILE_STORAGE + "/launcher/delta/delta.hash").writeText(newHash)
                        disableDeltaPlugin()
                        runDeltaCommand(command, "Running Delta Plugin...", Constants.USER_FILE_STORAGE + "/launcher/delta/delta.log")
                        enableDeltaPlugin()

                    }
                    .setPositiveButton("Cancel") { _, _ ->
                    }
                    .setNegativeButton(if (!isGenerated) "" else if (isEnabled) "Disable" else "Enable") { _, _ ->
                        if (isGenerated) {
                            if (!isEnabled)
                                enableDeltaPlugin()
                            else
                                disableDeltaPlugin()
                        }
                    }
                    .show()

                true
            }

            R.id.action_tools_groundcoverify -> {

                var isGenerated = false
                var isEnabled = false
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/output_deleted.omwaddon").exists() &&
                    File(Constants.USER_FILE_STORAGE + "/launcher/delta/output_groundcover.omwaddon").exists())
                    isGenerated = true

                val lines = File(Constants.USER_CONFIG + "/openmw.cfg").readLines().toMutableList()
                lines.forEach { 
                    if (it.contains("groundcover=output_groundcover.omwaddon"))
                        isEnabled = true
                }

                var oldHash = ""
                if (File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.hash").exists())
                    oldHash = File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.hash").readText()
                val newHash = generateDeltaCfg().hashCode().toString()

                var message = "Groundcoverify is "
                if (!isGenerated) message += "not generated!"
                else if (!isEnabled) message += "disabled."
                else message += "enabled."

                if (isGenerated && oldHash != newHash && isEnabled)
                    message += "\n\nAnd may be outdated."
                else if (isGenerated && isEnabled)
                    message += "\n\nAnd is up to date."

           
                AlertDialog.Builder(this)
                    .setTitle("Groundcoverify")
                    .setMessage(message)
                    .setNeutralButton(if (isGenerated) "Re-Generate" else "Generate") { _, _ ->
                        File(Constants.USER_FILE_STORAGE + "/launcher/delta/groundcoverify.hash").writeText(newHash)
                        disableGroundcoverify()
                        runGroundcoverify()
                        enableGroundcoverify()

                    }
                    .setPositiveButton("Cancel") { _, _ ->
                    }
                    .setNegativeButton(if (!isGenerated) "" else if (isEnabled) "Disable" else "Enable") { _, _ ->
                        if (isGenerated) {
                            if (!isEnabled)
                                enableGroundcoverify()
                            else
                                disableGroundcoverify()
                        }
                    }
                    .show()

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupData(path: String) {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        updateModList()
        with(sharedPref.edit()) {
            putString("mods_dir", path + "/")
            putString("mods_database", "Default")
            apply()
        }

        reloadModLists()
    }
}
