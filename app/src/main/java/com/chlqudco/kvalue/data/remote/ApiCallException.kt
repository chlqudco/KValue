package com.chlqudco.kvalue.data.remote

import com.chlqudco.kvalue.common.AppError

internal class ApiCallException(
    val error: AppError
) : Exception()
