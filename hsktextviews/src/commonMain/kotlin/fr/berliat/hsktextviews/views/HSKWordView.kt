import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import fr.berliat.hsktextviews.PinyinUtils.isHanzi
import fr.berliat.hsktextviews.views.HSKCharView

@Composable
fun HSKWordView(
    hanziText: String,
    pinyinText: String,
    pinyinEditable: Boolean = false,
    isClicked: Boolean = false,
    onWordClick: ((String) -> Unit)? = null,
    pinyinStyle: TextStyle = TextStyle(
        fontSize = 18.sp,
        color = Color.Gray
    ),
    hanziStyle: TextStyle = TextStyle(
        fontSize = 30.sp,
        color = Color.Black,
        fontWeight = FontWeight.Bold
    ),
    clickedPinyinStyle: TextStyle = TextStyle(
        fontSize = 18.sp,
        color = Color.White,
    ),
    clickedHanziStyle: TextStyle = TextStyle(
        fontSize = 30.sp,
        color = Color.White,
        fontWeight = FontWeight.Bold
    ),
    clickedBackgroundColor: Color = Color.Black,
    onPinyinChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pinyinList = pinyinText.split(" ")
    val extPinyinList = mutableListOf<String>()

    FlowRow(
        modifier = if (onWordClick !=null) modifier.clickable { onWordClick.invoke(hanziText) } else modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.5.dp)
    ) {
        var pinyinIndex = -1
        hanziText.forEachIndexed { index, hanzi ->
            extPinyinList.add(
                if (hanzi.isHanzi()) {
                    pinyinIndex++

                    if (pinyinIndex < pinyinList.size) {
                        pinyinList[pinyinIndex]
                    } else {
                        ""
                    }
                } else {
                    ""
                }
            )

            HSKCharView(
                hanzi,
                initialPinyin = extPinyinList[index],
                pinyinEditable,
                autoAddFlatTones = index == hanziText.length - 1,
                onPinyinChange = { newPinyin ->
                    if (extPinyinList[index] != newPinyin) {
                        extPinyinList[index] = newPinyin
                        onPinyinChange(extPinyinList.map { it.trim() }.filter { it.isNotEmpty() }
                            .joinToString(" "))
                    }
                },
                pinyinStyle = if (isClicked) clickedPinyinStyle else pinyinStyle,
                hanziStyle = if (isClicked) clickedHanziStyle else hanziStyle,
                modifier = Modifier.background(if (isClicked) clickedBackgroundColor else Color.Transparent)
            )
        }
    }
}