package dev.pixelhome

import org.json.JSONObject
import java.util.UUID

/** Unauthenticated identity only: no pairing token, state, commands, or arbitrary URL. */
object TabletDiscoveryProtocol {
    const val PORT=8787
    const val SERVICE="_hearthline._tcp."
    const val PATH="/hearthline/discovery/v1"
    const val MAX_HEADER=2048
    fun metadata(tabletId: String, appVersion: String): String {
        require(UUID.fromString(tabletId).toString()==tabletId)
        require(appVersion.length in 1..32 && appVersion.none { it.code<32 })
        return JSONObject().put("schema_version",1).put("tablet_id",tabletId)
            .put("app_version",appVersion).put("name","Hearthline tablet").toString()
    }
    fun validRequest(header: String): Boolean {
        if(header.length>MAX_HEADER || !header.endsWith("\r\n\r\n")) return false
        if(header.any { it.code !in 32..126 && it!='\r' && it!='\n' }) return false
        val lines=header.split("\r\n")
        if(lines.first() !in setOf("GET $PATH HTTP/1.1","GET $PATH HTTP/1.0")) return false
        return lines.drop(1).all { line ->
            if(line.isEmpty()) true else {
                val parts=line.split(':',limit=2)
                parts.size==2 && !parts[0].equals("Transfer-Encoding",true) &&
                    (!parts[0].equals("Content-Length",true) || parts[1].trim()=="0")
            }
        }
    }
}
