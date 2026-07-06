package com.example.compose_clean_architecture.presentation.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style
    )
}

@Composable
fun AppText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    maxLines: Int = Int.MAX_VALUE,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        overflow = overflow,
        maxLines = maxLines,
        style = style
    )
}

object AppText {
    // Display
    @Composable
    fun DisplayLarge(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.displayLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun DisplayLargeBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.displayLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun DisplayMedium(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.displayMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun DisplayMediumBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.displayMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun DisplaySmall(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.displaySmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun DisplaySmallBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.displaySmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    // Headlines
    @Composable
    fun HeadlineLarge(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.headlineLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun HeadlineLargeBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.headlineLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun HeadlineMedium(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.headlineMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun HeadlineMediumBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.headlineMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun HeadlineSmall(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.headlineSmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun HeadlineSmallBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.headlineSmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    // Titles
    @Composable
    fun TitleLarge(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.titleLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun TitleLargeBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.titleLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun TitleMedium(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.titleMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun TitleMediumBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.titleMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun TitleSmall(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.titleSmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun TitleSmallBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.titleSmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    // Labels
    @Composable
    fun LabelLarge(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.labelLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun LabelLargeBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.labelLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun LabelMedium(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.labelMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun LabelMediumBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.labelMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun LabelSmall(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.labelSmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun LabelSmallBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.labelSmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    // Body
    @Composable
    fun BodyLarge(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.bodyLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun BodyLargeBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.bodyLarge
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun BodyMedium(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.bodyMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun BodyMediumBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.bodyMedium
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun BodySmall(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = null,
        style: TextStyle = MaterialTheme.typography.bodySmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )

    @Composable
    fun BodySmallBold(
        text: String,
        modifier: Modifier = Modifier,
        color: Color = Color.Unspecified,
        textAlign: TextAlign? = null,
        overflow: TextOverflow = TextOverflow.Ellipsis,
        maxLines: Int = Int.MAX_VALUE,
        fontWeight: FontWeight? = FontWeight.Bold,
        style: TextStyle = MaterialTheme.typography.bodySmall
    ) = AppText(
        text,
        modifier,
        color,
        TextUnit.Unspecified,
        fontWeight,
        textAlign,
        overflow,
        maxLines,
        style
    )
}
