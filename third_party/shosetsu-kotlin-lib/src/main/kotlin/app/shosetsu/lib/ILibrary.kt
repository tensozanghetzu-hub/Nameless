package app.shosetsu.lib

import app.shosetsu.lib.exceptions.InvalidMetaDataException
import app.shosetsu.lib.json.J_AUTHOR
import app.shosetsu.lib.json.J_DEP
import app.shosetsu.lib.json.J_REPO
import app.shosetsu.lib.json.J_VERSION
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.luaj.vm2.LuaError

/*
 * This file is part of shosetsu-services.
 * shosetsu-services is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * shosetsu-services is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with shosetsu-services.  If not, see https://www.gnu.org/licenses/.
 */

interface ILibrary {
    @Serializable
    data class LibMetaData(
        @SerialName(J_VERSION)
        val version: Version,
        @SerialName(J_AUTHOR)
        val author: String = "",
        @SerialName(J_REPO)
        val repo: String = "",
        @SerialName(J_DEP)
        val stringDep: List<String> = listOf()
    )

    /** Metadata of the library */
    @get:Throws(LuaError::class, InvalidMetaDataException::class)
    val libMetaData: LibMetaData
}