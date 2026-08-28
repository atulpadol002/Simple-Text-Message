package com.ap.simpletextmessage.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ap.simpletextmessage.R

@Composable
fun EmojiPicker(
    recentEmojis: List<String>,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categories = remember(recentEmojis, context.resources.configuration.locales) {
        buildList {
            if (recentEmojis.isNotEmpty()) {
                add(EmojiCategory(context.getString(R.string.emoji_recent), "🕘", recentEmojis))
            }
            addAll(defaultEmojiCategories(context))
        }
    }
    var selectedCategoryIcon by rememberSaveable { mutableStateOf("😀") }
    val selectedIndex = categories.indexOfFirst { it.icon == selectedCategoryIcon }
        .coerceAtLeast(0)

    // Each category keeps its own grid position while the picker remains in composition.
    val gridStates = categories.associate { category ->
        category.icon to key(category.icon) { rememberLazyGridState() }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(320.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column {
            Text(
                text = categories[selectedIndex].name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
            )
            EmojiCategoryBar(
                categories = categories,
                selectedIndex = selectedIndex,
                onCategorySelected = { selectedCategoryIcon = categories[it].icon },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider()
            EmojiGrid(
                emojis = categories[selectedIndex].emojis,
                state = checkNotNull(gridStates[categories[selectedIndex].icon]),
                onEmojiSelected = onEmojiSelected,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

private fun defaultEmojiCategories(context: Context) = listOf(
    EmojiCategory(context.getString(R.string.emoji_smileys), "😀", "😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥳 😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫣 🤭 🫢 🫡 🤫 🫠 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🤢 🤮 🤧 😷 🤒 🤕".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_animals), "🐻", "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 🐌 🐞 🐜 🪰 🪲 🪳 🦟 🦗 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦀 🦞 🐠 🐟 🐬 🐳 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦛 🦏 🐪 🦒 🦘 🦬 🐃 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐈 🪶 🦃 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 🦦 🦥 🐁 🐿️ 🦔".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_food), "🍔", "🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🥐 🥯 🍞 🥖 🥨 🧀 🥚 🍳 🧈 🥞 🧇 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🫓 🥪 🥙 🧆 🌮 🌯 🫔 🥗 🥘 🫕 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🦪 🍤 🍙 🍚 🍘 🍥 🥠 🥮 🍢 🍡 🍧 🍨 🍦 🥧 🧁 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 ☕ 🫖 🍵 🧃 🥤 🧋 🍺 🍷 🍸 🍹".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_activities), "⚽", "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🪂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚵 🚴 🏆 🥇 🥈 🥉 🏅 🎖️ 🏵️ 🎗️ 🎫 🎟️ 🎪 🤹 🎭 🩰 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 ♟️ 🎯 🎳 🎮 🎰 🧩".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_travel), "🚗", "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🦯 🦽 🦼 🛴 🚲 🛵 🏍️ 🛺 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🛰️ 🚀 🛸 🚁 🛶 ⛵ 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ 🪝 ⛽ 🚧 🚦 🚥 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏕️ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 🏭 🏛️ ⛪ 🕌 🛕 🕍 🕋 ⛩️ 🛤️ 🌅 🌄 🌠 🎇 🎆 🌇 🌆 🏙️ 🌃 🌌 🌉".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_objects), "💡", "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 📷 📸 📹 🎥 📞 ☎️ 📺 📻 🎙️ ⏱️ ⏰ ⌛ 🔋 🔌 💡 🔦 🕯️ 🧯 🛢️ 💸 💵 💴 💶 💷 🪙 💳 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 ⚒️ 🛠️ ⛏️ 🪚 🔩 ⚙️ 🧱 ⛓️ 🧲 🔫 💣 🧨 🪓 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🪦 ⚱️ 🔮 📿 🧿 💈 ⚗️ 🔭 🔬 🕳️ 🩹 🩺 💊 💉 🩸 🧬 🦠 🧹 🧺 🧻 🚽 🚿 🛁 🧼 🪥 🪒 🧽 🪣 🧴 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🧸 🖼️ 🛍️ 🛒 🎁 🎈 🎏 🎀 🪄 🪅 🎊 🎉 ✉️ 📦 📚 📌 ✂️ 📝 🔒".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_symbols), "❤️", "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 ❤️‍🩹 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ ☢️ ☣️ 📴 📳 🈶 🈚 🈸 🈺 🈷️ ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️ 🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 🚭 ❗ ❕ ❓ ❔ ‼️ ⁉️ ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ ❎ 🌐 💠 Ⓜ️ 🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🛂 🛃 🛄 🛅".split(" ")),
    EmojiCategory(context.getString(R.string.emoji_flags), "🏁", "🏁 🚩 🎌 🏴 🏳️ 🏳️‍🌈 🏳️‍⚧️ 🏴‍☠️ 🇮🇳 🇺🇸 🇬🇧 🇨🇦 🇦🇺 🇯🇵 🇨🇳 🇰🇷 🇩🇪 🇫🇷 🇮🇹 🇪🇸 🇧🇷 🇲🇽 🇦🇷 🇿🇦 🇳🇬 🇪🇬 🇸🇦 🇦🇪 🇸🇬 🇲🇾 🇮🇩 🇹🇭 🇻🇳 🇵🇭 🇳🇵 🇧🇩 🇱🇰 🇵🇰 🇳🇿 🇮🇪 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇵🇱 🇺🇦 🇬🇷 🇹🇷 🇮🇱 🇧🇹 🇲🇻 🇰🇪 🇬🇭 🇲🇦 🇯🇲 🇨🇱 🇨🇴 🇵🇪 🇺🇾 🇻🇦 🇺🇳".split(" "))
)
