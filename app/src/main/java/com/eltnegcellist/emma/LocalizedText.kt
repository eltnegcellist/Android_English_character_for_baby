package com.eltnegcellist.emma

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
internal fun LocalizedText(text: String, modifier: Modifier=Modifier, color: Color=Color.Unspecified,
    fontSize: TextUnit=TextUnit.Unspecified,fontStyle: FontStyle?=null,fontWeight: FontWeight?=null,
    fontFamily: FontFamily?=null,letterSpacing: TextUnit=TextUnit.Unspecified,textDecoration: TextDecoration?=null,
    textAlign: TextAlign?=null,lineHeight: TextUnit=TextUnit.Unspecified,overflow: TextOverflow=TextOverflow.Clip,
    softWrap: Boolean=true,maxLines: Int=Int.MAX_VALUE,minLines: Int=1,
    onTextLayout: ((TextLayoutResult)->Unit)?=null,style: TextStyle=androidx.compose.material3.LocalTextStyle.current) {
    Text(text=AppLanguage.translate(text),modifier=modifier,color=color,fontSize=fontSize,fontStyle=fontStyle,
        fontWeight=fontWeight,fontFamily=fontFamily,letterSpacing=letterSpacing,textDecoration=textDecoration,
        textAlign=textAlign,lineHeight=lineHeight,overflow=overflow,softWrap=softWrap,maxLines=maxLines,
        minLines=minLines,onTextLayout=onTextLayout,style=style)
}

@Composable internal fun LanguagePicker(enabled: Boolean=true,compact: Boolean=false,onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        if(!compact) LocalizedText("言語を選択")
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=if(compact) Modifier else Modifier.fillMaxWidth()) {
            Text(if(AppLanguage.choiceRequired.value) "Select language" else if(compact && AppLanguage.language=="en") "Multilingual" else AppLanguage.names[AppLanguage.language].orEmpty())
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            AppLanguage.supported.forEach { code -> DropdownMenuItem(text={Text(AppLanguage.names.getValue(code))},
                onClick={expanded=false;onSelect(code)}) }
        }
        if(!compact) LocalizedText(if(AppLanguage.language=="en") "英語表示で、さまざまな言語の音声を自動認識します。" else "選択した言語で話しかけると、短い英語で返します。")
    }
}
