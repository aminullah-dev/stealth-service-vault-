package com.security.stealthapp.data

import com.security.stealthapp.data.db.dao.SalonDao
import com.security.stealthapp.data.db.dao.UserDao
import com.security.stealthapp.data.db.entities.Salon
import com.security.stealthapp.data.db.entities.User
import com.security.stealthapp.data.db.entities.UserRole
import com.security.stealthapp.security.PinHasher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the encrypted database with demo accounts on the very first launch.
 *
 * Demo PINs (share these only with test users):
 *   Customer  →  PIN 5678   (Fatima)
 *   Provider  →  PIN 1234   (Maryam Studio)
 *   Provider  →  PIN 2345   (Zahra Beauty)
 *   Provider  →  PIN 3456   (Parisa Brows)
 *
 * Call [seedIfEmpty] once from Application.onCreate() on a background thread.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val userDao: UserDao,
    private val salonDao: SalonDao,
    private val pinHasher: PinHasher
) {

    suspend fun seedIfEmpty() {
        if (userDao.getUserCount() > 0) return // already seeded

        // ── Customer ──────────────────────────────────────────────────────────
        val cSalt = pinHasher.generateSalt()
        val customer = User(
            name        = "Fatima",
            phoneNumber = "0799-100001",
            role        = UserRole.CUSTOMER,
            pinHash     = pinHasher.hash("5678", cSalt),
            salt        = cSalt
        )
        userDao.insert(customer)

        // ── Provider 1: Maryam ────────────────────────────────────────────────
        val p1Salt = pinHasher.generateSalt()
        val maryam = User(
            name        = "Maryam",
            phoneNumber = "0700-200001",
            role        = UserRole.PROVIDER,
            pinHash     = pinHasher.hash("1234", p1Salt),
            salt        = p1Salt
        )
        userDao.insert(maryam)
        salonDao.insert(
            Salon(
                providerId  = maryam.id,
                salonName   = "Maryam Studio",
                district    = "District 3 – Khair Khana",
                services    = listOf("Haircut", "Colour", "Braids", "Keratin Treatment"),
                isAvailable = true,
                rating      = 4.9f
            )
        )

        // ── Provider 2: Zahra ─────────────────────────────────────────────────
        val p2Salt = pinHasher.generateSalt()
        val zahra = User(
            name        = "Zahra",
            phoneNumber = "0700-200002",
            role        = UserRole.PROVIDER,
            pinHash     = pinHasher.hash("2345", p2Salt),
            salt        = p2Salt
        )
        userDao.insert(zahra)
        salonDao.insert(
            Salon(
                providerId  = zahra.id,
                salonName   = "Zahra Beauty",
                district    = "District 6 – Karte Seh",
                services    = listOf("Bridal Makeup", "Party Makeup", "Natural Look", "HD Makeup"),
                isAvailable = true,
                rating      = 4.8f
            )
        )

        // ── Provider 3: Parisa ────────────────────────────────────────────────
        val p3Salt = pinHasher.generateSalt()
        val parisa = User(
            name        = "Parisa",
            phoneNumber = "0700-200003",
            role        = UserRole.PROVIDER,
            pinHash     = pinHasher.hash("3456", p3Salt),
            salt        = p3Salt
        )
        userDao.insert(parisa)
        salonDao.insert(
            Salon(
                providerId  = parisa.id,
                salonName   = "Parisa Brows",
                district    = "District 11 – Qala-e Wahed",
                services    = listOf("Threading", "Henna Brows", "Micro-blading", "Waxing"),
                isAvailable = true,
                rating      = 4.9f
            )
        )

        // ── Provider 4: Neda ─────────────────────────────────────────────────
        val p4Salt = pinHasher.generateSalt()
        val neda = User(
            name        = "Neda",
            phoneNumber = "0700-200004",
            role        = UserRole.PROVIDER,
            pinHash     = pinHasher.hash("4567", p4Salt),
            salt        = p4Salt
        )
        userDao.insert(neda)
        salonDao.insert(
            Salon(
                providerId  = neda.id,
                salonName   = "Neda Nails Pro",
                district    = "District 9 – Dasht-e Barchi",
                services    = listOf("Gel Nails", "Acrylic", "Nail Art", "French Tip"),
                isAvailable = true,
                rating      = 4.7f
            )
        )
    }
}
