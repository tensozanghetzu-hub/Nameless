// Nameless fork: modified 2026-10-05; see NAMELESS.md.
// Upstream contributors frozen from Shosetsu v2.5.3 (f1036d2b).
// Generated from the original Git history; GPL-3.0, like the upstream app.
package app.shosetsu.android.domain.repository.impl

import app.shosetsu.android.domain.model.local.Contributor
import app.shosetsu.android.domain.repository.base.ContributorsRepository

class ContributorsRepositoryImpl : ContributorsRepository {
    override fun getAll(): List<Contributor> = listOf(
        Contributor(name = "Clocks", email = "me@doomsdayrs.page", commits = 2590, website = "https://doomsdayrs.page", image = "https://gitlab.com/uploads/-/system/user/avatar/3931112/avatar.png?width=256"),
        Contributor(name = "Clocks", email = "rahim13657", commits = 586, website = "https://doomsdayrs.page", image = "https://gitlab.com/uploads/-/system/user/avatar/3931112/avatar.png?width=256"),
        Contributor(name = "JFronny", email = "git@akbvopenfl1.hopto.org", commits = 155, website = "https://jfronny.gitlab.io", image = "https://gitlab.com/uploads/-/system/user/avatar/6260391/avatar.png?width=256"),
        Contributor(name = "Jobobby04", email = "jobobby04@users.noreply.github.com", commits = 146, website = null, image = null),
        Contributor(name = "Clocks", email = "38189170+Doomsdayrs@users.noreply.github.com", commits = 49, website = "https://doomsdayrs.page", image = "https://gitlab.com/uploads/-/system/user/avatar/3931112/avatar.png?width=256"),
        Contributor(name = "Suhan G Paradkar", email = "12suhangp34@gmail.com", commits = 25, website = null, image = null),
        Contributor(name = "TechnoJo4", email = "technojo4@gmail.com", commits = 25, website = null, image = null),
        Contributor(name = "Clocks", email = "3931112-Doomsdayrs@users.noreply.gitlab.com", commits = 23, website = "https://doomsdayrs.page", image = "https://gitlab.com/uploads/-/system/user/avatar/3931112/avatar.png?width=256"),
        Contributor(name = "Harsh Parekh", email = "h.x.dev@outlook.com", commits = 20, website = null, image = null),
        Contributor(name = "Kyle Mills", email = "khonkhortisan@gmail.com", commits = 4, website = null, image = null),
        Contributor(name = "Markus Koas", email = "markus@mkoas.de", commits = 4, website = null, image = null),
        Contributor(name = "daniil", email = "den4ic2001@gmail.com", commits = 4, website = null, image = null),
        Contributor(name = "wasu", email = "61418403+wasu-code@users.noreply.github.com", commits = 4, website = null, image = null),
        Contributor(name = "Andrew Bezold", email = "andrew.bezold@gmail.com", commits = 3, website = null, image = null),
        Contributor(name = "Jobobby04", email = "jobobby04@gmail.com", commits = 3, website = null, image = null),
        Contributor(name = "miko_da_freako", email = "53712054+mikodafreako@users.noreply.github.com", commits = 2, website = null, image = null),
        Contributor(name = "Blatzar", email = "46196380+Blatzar@users.noreply.github.com", commits = 1, website = null, image = null),
        Contributor(name = "Kyle Mills", email = "Khonkhortisan@gmail.com", commits = 1, website = null, image = null),
        Contributor(name = "Mikalai Kukhta", email = "mikalai.kukhta@gmail.com", commits = 1, website = null, image = null),
        Contributor(name = "Mukunda Dev Adhikari", email = "44703575+AERegeneratel38@users.noreply.github.com", commits = 1, website = null, image = null),
        Contributor(name = "Rider21", email = "selagin33@gmail.com", commits = 1, website = null, image = null),
        Contributor(name = "abidin24", email = "toumiabidin@gmail.com", commits = 1, website = null, image = null),
        Contributor(name = "roshavagarga", email = "roshavagarga@users.noreply.github.com", commits = 1, website = null, image = null),
        Contributor(name = "wasu", email = "wsu808@int.pl", commits = 1, website = null, image = null)
    )
}
